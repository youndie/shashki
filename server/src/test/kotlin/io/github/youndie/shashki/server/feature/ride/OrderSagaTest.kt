package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichCheck
import io.github.youndie.petich.PetichCheckContext
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichPhase
import io.github.youndie.petich.PetichResult
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.petich.petich
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
import io.github.youndie.shashki.server.feature.ride.saga.orderPetich
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.ServiceArea
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import io.github.youndie.shashki.server.testing.FixedCandidateSource
import io.github.youndie.shashki.server.testing.PostgresHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the engine needs a clock and this test asserts on the saga's phases, not on time",
    )
    private val clock = PetichClock { System.currentTimeMillis() }
    private val timeouts = OfferTimeouts(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { _, _ -> }

    private fun offerStep(candidates: FixedCandidateSource = FixedCandidateSource()) =
        OfferStep(candidates, reservations, InMemoryOfferBoard(), clock, timeouts)

    // THE PRODUCTION FACTORY, not a hand-built copy of it. A list assembled here would be a second
    // declaration of the saga's order, and the one that drifts is the one nobody deploys.
    private fun definitionWith(
        candidates: FixedCandidateSource = FixedCandidateSource(),
    ): PetichDefinition<OrderPayload> =
        orderPetich(
            routes = StraightLineRouteEstimator(),
            pricing = Pricing(),
            area = { ServiceArea.LJUBLJANA },
            payments = payments,
            offers = offerStep(candidates),
            candidates = candidates,
            reservations = reservations,
            json = json,
        )

    private val definition: PetichDefinition<OrderPayload> = definitionWith()

    private fun engineWith(definition: PetichDefinition<OrderPayload> = this.definition) =
        sagaEngine(storage, clock, definitions = listOf(definition))

    /** The saga stops to ask the nearest driver; the driver says yes. Two passes, as in production. */
    private suspend fun runToAssigned(
        engine: io.github.youndie.petich.PetichEngine,
        id: String,
    ): PetichResult {
        val parked = engine.process(order(id))
        if (parked !is PetichResult.ActionRequired) return parked
        val saga = checkNotNull(storage.petiches.findById(id))
        return engine.process(saga.copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.ACCEPT)))
    }

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    /**
     * **The names, asserted as data.** They went to the collector with the dollar sign still in them
     * — `saga.order.$phase.QuoteStep`, one unescaped template, invisible to the compiler and to
     * every test here, and found by reading what had actually arrived in tracy. A span whose name is
     * wrong is worse than a missing one: it groups every phase of every saga under one row.
     */
    @Test
    fun `every member's span name is its own key`() {
        // THE SAME ASSERTION IT ALWAYS MADE, addressed differently. The name used to carry the
        // phase, because a step knew its phase; a member's address is its key now, so that is what
        // the name carries — and the key is stricter, since the builder refuses two members under
        // one key and two spans therefore cannot collide.
        val keys = definition.members.map { it.key }
        assertEquals(6, keys.size, "a member outside the definition is a member with no span")

        keys.forEach { key ->
            val name = OrderStep.spanName(key)
            assertFalse('$' in name, "an unexpanded template: $name")
            assertTrue(name.startsWith("saga.order."), "$name does not say which saga it belongs to")
            assertTrue(name.endsWith(key), "$name does not name the member it belongs to")
        }
        assertEquals(keys.size, keys.map { OrderStep.spanName(it) }.toSet().size, "two members share a span name")
    }

    @Test
    fun `a ride runs through every phase, holds the fare, reserves a driver and leaves one event in the outbox`() =
        runTest {
            val result = runToAssigned(engineWith(), "ride-ok")

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
            for (dieBefore in listOf("service-area", "hold-payment", "offer", "publish-assigned")) {
                val engine = engineWith(definitionDying(at = dieBefore))
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
            // What `kill -9` after the hold committed actually leaves: a PROCESSING row parked
            // INSIDE EXECUTION at the member after `hold-payment`, with the quote and the hold id
            // in its enriched payload — and a real hold in the gateway, because that side effect
            // happened before the death.
            //
            // The index is what makes this safe, not the phase. The hold used to sit in
            // AUTHORIZATION, and it was tempting to read this test as "a committed phase boundary
            // protects the hold from running twice". It never did: petich commits
            // `currentInterceptorIndex = index + 1` after EVERY member that proceeds, and writes
            // nothing at all when a phase ends. So the row that survives a death names the member,
            // and moving `hold-payment` from AUTHORIZATION into EXECUTION moved the number from
            // (AUTHORIZATION, past-the-end) to (EXECUTION, 1) without weakening anything.
            // `HoldPaymentStep` is not idempotent and does not need to be.
            //
            // Reconstructed by hand rather than staged with a fake step, because a step that
            // *suspends* leaves a different row (PENDING_SIGNATURE, waiting for a resume payload)
            // and a step that *throws* is compensated on the spot; neither is a dead process.
            val hold = payments.hold("card-4417", amountCents = 1_000, currency = "USD")
            val parked =
                order("ride-resumed").copy(
                    status = PetichStatus.PROCESSING,
                    currentPhase = PetichPhase.EXECUTION,
                    // EXECUTION is [hold-payment, offer, driver-answer]; 1 is "the hold is done".
                    currentInterceptorIndex = 1,
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
            // for that id. It continues at the member the index names — not re-running the hold
            // and holding twice — asks a driver and parks; the driver's answer finishes it.
            val engineB = engineWith()
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
                engineWith(definitionWith(FixedCandidateSource(emptyList()))).process(order("ride-no-cars"))

            assertIs<PetichResult.Error>(result)
            assertEquals(emptyList(), payments.activeHolds().toList())
        }

    @Test
    fun `a pickup outside the service area is refused before anything is held`() =
        runTest {
            val far = order("ride-far", pickup = GeoPoint(48.8566, 2.3522))

            val result = engineWith().process(far)

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

    /**
     * A death is the member never returning, addressed by key rather than by phase.
     *
     * Written out rather than mapped over a list: the order is a declaration now, and a test that
     * rebuilt it from a filter would be asserting against its own copy of it.
     */
    private fun definitionDying(at: String): PetichDefinition<OrderPayload> {
        val candidates = FixedCandidateSource()
        val offers = offerStep(candidates)
        return petich(ORDER_SAGA_TYPE) {
            enrich("quote", QuoteStep(StraightLineRouteEstimator(), Pricing()))
            if (at == "service-area") {
                validate(at, DyingCheck(at))
            } else {
                validate("service-area", ServiceAreaStep { ServiceArea.LJUBLJANA })
            }
            if (at == "hold-payment") step(at, Dying(at)) else step("hold-payment", HoldPaymentStep(payments))
            if (at == "offer") step(at, Dying(at)) else step("offer", offers)
            step("driver-answer", DriverAnswerStep(candidates, reservations, offers))
            if (at == "publish-assigned") {
                announce(at, Dying(at))
            } else {
                announce("publish-assigned", PublishAssignedStep(json))
            }
        }
    }

    private class Dying(
        private val key: String,
    ) : PetichStep<OrderPayload> {
        override suspend fun execute(
            ctx: PetichStepContext,
            payload: OrderPayload,
        ): Unit = error("process died before $key answered")

        override suspend fun compensate(
            ctx: PetichStepContext,
            payload: OrderPayload,
        ) = Unit
    }

    private class DyingCheck(
        private val key: String,
    ) : PetichCheck<OrderPayload> {
        override suspend fun check(
            ctx: PetichCheckContext,
            payload: OrderPayload,
        ): Unit = error("process died before $key answered")
    }

    private companion object {
        val LJUBLJANA_CENTRE = GeoPoint(46.0511, 14.5051)
        val LJUBLJANA_AIRPORT = GeoPoint(46.2237, 14.4576)
    }
}
