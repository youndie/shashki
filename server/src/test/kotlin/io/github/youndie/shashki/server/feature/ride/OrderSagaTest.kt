package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.dispatch.InMemoryDriverReservations
import io.github.youndie.shashki.server.dispatch.InMemoryOfferBoard
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswer
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswerStep
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.HoldPaymentStep
import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.OfferStep
import io.github.youndie.shashki.server.feature.ride.saga.OfferTimeouts
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.ride.saga.OrderStep
import io.github.youndie.shashki.server.feature.ride.saga.PublishAssignedStep
import io.github.youndie.shashki.server.feature.ride.saga.QuoteStep
import io.github.youndie.shashki.server.feature.ride.saga.RideAssignedEvent
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.ServiceAreaStep
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import io.github.youndie.shashki.server.testing.FixedCandidateSource
import io.github.youndie.shashki.server.testing.PostgresHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import ru.workinprogress.petich.InterceptorResult
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SimpleEnrichedPayload
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The order saga against a real Postgres, and B-11's first acceptance criterion made literal:
 * **whichever step the process dies after, no hold and no reserved driver are left behind.**
 */
class OrderSagaTest {
    private val json = sagaJson()
    private val storage = SagaStorage(PostgresHarness.database, json)
    private val payments = InMemoryPaymentGateway()
    private val reservations = InMemoryDriverReservations()
    private val clock = PetichClock { System.currentTimeMillis() }
    private val timeouts = OfferTimeouts(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { _, _ -> }

    private fun offerStep(candidates: FixedCandidateSource = FixedCandidateSource()) =
        OfferStep(candidates, reservations, InMemoryOfferBoard(), clock, timeouts)

    private fun stepsWith(candidates: FixedCandidateSource = FixedCandidateSource()): List<PetichInterceptor<*>> {
        val offers = offerStep(candidates)
        return listOf(
            QuoteStep(StraightLineRouteEstimator(), Pricing()),
            ServiceAreaStep(),
            HoldPaymentStep(payments),
            offers,
            DriverAnswerStep(candidates, reservations, offers),
            PublishAssignedStep(json),
        )
    }

    private val steps: List<PetichInterceptor<*>> = stepsWith()

    /** The saga stops to ask the nearest driver; the driver says yes. Two passes, as in production. */
    private suspend fun runToAssigned(
        engine: ru.workinprogress.petich.PetichEngine,
        id: String,
    ): PetichResult {
        val parked = engine.process(order(id))
        if (parked !is PetichResult.ActionRequired) return parked
        val saga = checkNotNull(storage.petiches.findById(id))
        return engine.process(saga.copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.ACCEPT)))
    }

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a ride runs through every phase, holds the fare, reserves a driver and leaves one event in the outbox`() =
        runTest {
            val result = runToAssigned(sagaEngine(steps, storage, clock), "ride-ok")

            assertIs<PetichResult.Success>(result)
            assertEquals(PetichStatus.COMPLETED, result.petich.status)
            assertEquals(1, payments.activeHolds().size, "the fare is held, not captured — capture is the settlement's")
            assertEquals("driver-1", reservations.reservedFor("ride-ok"))

            val pending = storage.outbox.fetchPending()
            assertEquals(listOf(RideAssignedEvent.TYPE), pending.map { it.type })
            assertEquals(
                "ride-ok",
                json.decodeFromString(RideAssignedEvent.serializer(), pending.single().payload).rideId,
            )
        }

    @Test
    fun `dying after any phase leaves no held payment and no reserved driver`() =
        runTest {
            // The process "dies" at the boundary after phase N by the step of phase N+1 throwing —
            // which is what an unplugged process looks like to the saga: the next step never
            // returns. petich compensates 1..N. Every boundary before POST_PROCESSING is tried.
            for (dieBefore in listOf(
                PetichPhase.VALIDATION,
                PetichPhase.AUTHORIZATION,
                PetichPhase.EXECUTION,
                PetichPhase.POST_PROCESSING,
            )) {
                val engine = sagaEngine(steps.withDeathAt(dieBefore), storage, clock)
                val id = "ride-dies-before-$dieBefore"

                var result = engine.process(order(id))
                // Past EXECUTION the saga first parks for a driver; the death is on the pass that
                // the driver's answer starts, so answer it.
                if (result is PetichResult.ActionRequired) {
                    val saga = checkNotNull(storage.petiches.findById(id))
                    result =
                        engine.process(saga.copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.ACCEPT)))
                }

                assertTrue(result !is PetichResult.Success, "$dieBefore: the saga must not complete")
                assertEquals(emptyList(), payments.activeHolds().toList(), "$dieBefore: a hold survived the death")
                assertEquals(emptyMap(), reservations.all(), "$dieBefore: a driver stayed reserved")
                assertEquals(
                    emptyList(),
                    storage.outbox.fetchPending(),
                    "$dieBefore: an event escaped a saga that never completed",
                )
            }
        }

    @Test
    fun `a saga the first process abandoned is finished by the next one, from where it stopped`() =
        runTest {
            // What `kill -9` after AUTHORIZATION committed actually leaves: a PROCESSING row parked
            // at the start of EXECUTION, with the quote and the hold id in its enriched payload —
            // and a real hold in the gateway, because that side effect happened before the death.
            // Reconstructed by hand rather than staged with a fake step, because a step that
            // *suspends* leaves a different row (PENDING_SIGNATURE, waiting for a resume payload)
            // and a step that *throws* is compensated on the spot; neither is a dead process.
            val hold = payments.hold("card-4417", amountCents = 1_000, currency = "USD")
            val parked =
                order("ride-resumed").copy(
                    status = PetichStatus.PROCESSING,
                    currentPhase = PetichPhase.EXECUTION,
                    currentInterceptorIndex = 0,
                    enrichedPayload =
                        SimpleEnrichedPayload(
                            mapOf(
                                Enriched.QUOTE_DISTANCE to "20500",
                                Enriched.QUOTE_DURATION to "1560",
                                Enriched.QUOTE_AMOUNT to "1000",
                                Enriched.QUOTE_CURRENCY to "USD",
                                Enriched.HOLD_ID to hold.value,
                            ),
                        ),
                )
            storage.petiches.saveOrGet(parked)

            // A fresh process picks the row up: the sweeper, a retried request, or the next call
            // for that id. It continues at EXECUTION — not re-running AUTHORIZATION and holding
            // twice — asks a driver and parks; the driver's answer finishes it.
            val engineB = sagaEngine(steps, storage, clock)
            val firstPass = engineB.process(checkNotNull(storage.petiches.findById("ride-resumed")))
            assertIs<PetichResult.ActionRequired>(firstPass)
            assertEquals(
                listOf(hold),
                payments.activeHolds().map {
                    it.id
                },
                "resumed, not re-run: the one hold from before the death",
            )

            val resumed =
                engineB.process(
                    checkNotNull(storage.petiches.findById("ride-resumed"))
                        .copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.ACCEPT)),
                )

            assertIs<PetichResult.Success>(resumed)
            assertEquals(listOf(hold), payments.activeHolds().map { it.id }, "still the one hold after the answer")
            assertEquals("driver-1", reservations.reservedFor("ride-resumed"))
            assertEquals(listOf(RideAssignedEvent.TYPE), storage.outbox.fetchPending().map { it.type })
        }

    @Test
    fun `no cars nearby compensates the hold rather than leaving the rider charged`() =
        runTest {
            val result =
                sagaEngine(
                    stepsWith(FixedCandidateSource(emptyList())),
                    storage,
                    clock,
                ).process(order("ride-no-cars"))

            assertIs<PetichResult.Error>(result)
            assertEquals(emptyList(), payments.activeHolds().toList())
        }

    @Test
    fun `a pickup outside the service area is refused before anything is held`() =
        runTest {
            val far = order("ride-far", pickup = GeoPoint(48.8566, 2.3522))

            val result = sagaEngine(steps, storage, clock).process(far)

            assertIs<PetichResult.Error>(result)
            assertEquals(emptyList(), payments.activeHolds().toList())
            assertEquals(PetichStatus.REJECTED, checkNotNull(storage.petiches.findById("ride-far")).status)
        }

    private fun order(
        id: String,
        pickup: GeoPoint = LJUBLJANA_CENTRE,
        dropoff: GeoPoint = LJUBLJANA_AIRPORT,
    ): Petich =
        Petich(
            id = id,
            type = ORDER_SAGA_TYPE,
            status = PetichStatus.DRAFT,
            payload =
                OrderPayload(
                    id,
                    riderId = "rider-1",
                    pickup = pickup,
                    dropoff = dropoff,
                    rideClass = RideClass.COMFORT,
                    paymentMethodId = "card-4417",
                ),
        )

    /** The step at [phase] throws — the process died before it could answer. */
    private fun List<PetichInterceptor<*>>.withDeathAt(phase: PetichPhase): List<PetichInterceptor<*>> =
        map { step ->
            if (step.phase == phase) {
                object : OrderStep() {
                    override val phase = phase

                    override suspend fun intercept(
                        petich: Petich,
                        payload: OrderPayload,
                    ): InterceptorResult = error("process died before $phase answered")
                }
            } else {
                step
            }
        }

    private companion object {
        val LJUBLJANA_CENTRE = GeoPoint(46.0511, 14.5051)
        val LJUBLJANA_AIRPORT = GeoPoint(46.2237, 14.4576)
    }
}
