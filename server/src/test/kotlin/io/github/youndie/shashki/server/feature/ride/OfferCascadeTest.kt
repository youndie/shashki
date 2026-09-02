package io.github.youndie.shashki.server.feature.ride

import com.zaxxer.hikari.HikariDataSource
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.dispatch.InMemoryDriverReservations
import io.github.youndie.shashki.server.dispatch.InMemoryOfferBoard
import io.github.youndie.shashki.server.feature.ride.saga.ACTION_DRIVER_ANSWER
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswer
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswerStep
import io.github.youndie.shashki.server.feature.ride.saga.HoldPaymentStep
import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.OfferStep
import io.github.youndie.shashki.server.feature.ride.saga.OfferTimeouts
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.ride.saga.PublishAssignedStep
import io.github.youndie.shashki.server.feature.ride.saga.QuoteStep
import io.github.youndie.shashki.server.feature.ride.saga.RiderCancelled
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import ru.workinprogress.petich.ExpireResult
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SuspendedPetichSweeper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-12: the offer is a suspended saga. Three declines cascade with nothing held; a deadline nobody
 * meets rolls everything back; a rider's cancellation compensates from the middle.
 */
class OfferCascadeTest {
    private val json = sagaJson()
    private val storage = SagaStorage(PostgresHarness.database, json)
    private val payments = InMemoryPaymentGateway()
    private val reservations = InMemoryDriverReservations()
    private val board = InMemoryOfferBoard()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A clock the test moves, so "ninety seconds later" costs nothing. */
    private var now = 1_000_000L
    private val clock = PetichClock { now }

    private val fired = mutableListOf<Pair<String, String>>()
    private val timeouts = OfferTimeouts(scope) { rideId, driverId -> fired += rideId to driverId }
    private val offers = OfferStep(FixedCandidateSource(), reservations, board, clock, timeouts)

    private val steps: List<PetichInterceptor<*>> =
        listOf(
            QuoteStep(StraightLineRouteEstimator(), Pricing()),
            ServiceAreaStep(),
            HoldPaymentStep(payments),
            offers,
            DriverAnswerStep(FixedCandidateSource(), reservations, offers),
            PublishAssignedStep(json),
        )
    private val engine = sagaEngine(steps, storage, clock)

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @AfterTest
    fun stop() = scope.cancel()

    @Test
    fun `a request parks the saga waiting for the nearest driver, holding no connection`() =
        runTest {
            val result = engine.process(order("ride-park"))

            assertIs<PetichResult.ActionRequired>(result)
            assertEquals(ACTION_DRIVER_ANSWER, result.actionType)
            assertEquals("driver-1", board.forRide("ride-park")?.driverId)
            assertEquals("driver-1", reservations.reservedFor("ride-park"))
            assertEquals(1, payments.activeHolds().size, "the fare is held while the driver is asked")
            assertTrue(timeouts.pending("ride-park"), "the fifteen-second timer is running")
            assertEquals(0, activeConnections(), "a suspended saga holds no database connection")
        }

    @Test
    fun `three declines cascade to the next candidate, and the fourth answer has nobody left`() =
        runTest {
            engine.process(order("ride-cascade"))

            for ((attempt, driver) in listOf("driver-1", "driver-2", "driver-3").withIndex()) {
                assertEquals(driver, board.forRide("ride-cascade")?.driverId, "attempt $attempt asks $driver")
                assertEquals(0, activeConnections(), "between offers the pool is idle")
                val r =
                    engine.process(
                        suspended(
                            "ride-cascade",
                        ).copy(resumePayload = DriverAnswer(driver, DriverAnswer.Outcome.DECLINE)),
                    )
                assertNull(reservations.all()[driver], "$driver is freed on decline")
                if (attempt < 2) assertIs<PetichResult.ActionRequired>(r) else assertIs<PetichResult.Error>(r)
            }

            assertEquals(emptyMap(), reservations.all(), "no driver left reserved")
            assertNull(board.forRide("ride-cascade"), "no offer left on the board")
            assertEquals(emptyList(), payments.activeHolds().toList(), "no cars nearby releases the hold")
            // `Compensate` from a resumed step ends FAILED — petich's word for "rolled back"; REJECTED
            // is a `Reject` before anything happened. The rider sees CANCELLED either way.
            assertEquals(PetichStatus.FAILED, suspended("ride-cascade").status)
        }

    @Test
    fun `an accepted offer assigns the driver and leaves the hold for the settlement`() =
        runTest {
            engine.process(order("ride-accept"))
            engine.process(
                suspended("ride-accept").copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.DECLINE)),
            )

            val r =
                engine.process(
                    suspended(
                        "ride-accept",
                    ).copy(resumePayload = DriverAnswer("driver-2", DriverAnswer.Outcome.ACCEPT)),
                )

            assertIs<PetichResult.Success>(r)
            assertEquals("driver-2", reservations.reservedFor("ride-accept"))
            assertNull(board.forRide("ride-accept"), "an accepted offer leaves the board")
            assertEquals(1, payments.activeHolds().size)
            assertEquals(listOf("ride.assigned"), storage.outbox.fetchPending().map { it.type })
        }

    @Test
    fun `an ignored offer moves on like a decline, driven by the timer`() =
        runTest {
            engine.process(order("ride-ignored"))
            assertTrue(timeouts.pending("ride-ignored"))

            // What OfferTimeouts does at fifteen seconds, without waiting fifteen seconds.
            val r =
                engine.process(
                    suspended(
                        "ride-ignored",
                    ).copy(resumePayload = DriverAnswer("driver-1", DriverAnswer.Outcome.IGNORED)),
                )

            assertIs<PetichResult.ActionRequired>(r)
            assertEquals("driver-2", board.forRide("ride-ignored")?.driverId)
            assertNull(reservations.all()["driver-1"])
        }

    @Test
    fun `an answer from a driver who was not asked changes nothing`() =
        runTest {
            engine.process(order("ride-stranger"))

            val r =
                engine.process(
                    suspended(
                        "ride-stranger",
                    ).copy(resumePayload = DriverAnswer("driver-9", DriverAnswer.Outcome.ACCEPT)),
                )

            assertIs<PetichResult.ActionRequired>(r)
            assertEquals("driver-1", board.forRide("ride-stranger")?.driverId, "still asking the driver who was asked")
        }

    @Test
    fun `a deadline nobody answers rolls the saga back and frees the driver`() =
        runTest {
            engine.process(order("ride-silence"))
            now += OfferStep.MATCHING_BUDGET.inWholeMilliseconds + 1

            val swept =
                SuspendedPetichSweeper(storage.petiches, engineFor = {
                    engine
                }, clock = clock, pollInterval = Long.MAX_VALUE.let { kotlin.time.Duration.INFINITE }).sweep()

            assertEquals(1, swept, "the sweeper found the expired saga")
            assertEquals(emptyMap(), reservations.all(), "the driver is freed")
            assertEquals(emptyList(), payments.activeHolds().toList(), "the hold is released")
            assertNull(board.forRide("ride-silence"))
            assertTrue(suspended("ride-silence").status in setOf(PetichStatus.REJECTED, PetichStatus.FAILED))
        }

    @Test
    fun `the rider cancelling while a driver is asked compensates from the middle`() =
        runTest {
            engine.process(order("ride-cancel"))

            val r = engine.process(suspended("ride-cancel").copy(resumePayload = RiderCancelled()))

            assertIs<PetichResult.Error>(r)
            assertEquals(emptyMap(), reservations.all())
            assertEquals(emptyList(), payments.activeHolds().toList())
            assertNull(board.forRide("ride-cancel"))
        }

    private suspend fun suspended(id: String): Petich = checkNotNull(storage.petiches.findById(id))

    private fun activeConnections(): Int =
        (PostgresHarness.dataSource as HikariDataSource).hikariPoolMXBean.activeConnections

    private fun order(id: String): Petich =
        Petich(
            id = id,
            type = ORDER_SAGA_TYPE,
            status = PetichStatus.DRAFT,
            payload =
                OrderPayload(
                    id,
                    riderId = "rider-1",
                    pickup = GeoPoint(46.0511, 14.5051),
                    dropoff = GeoPoint(46.2237, 14.4576),
                    rideClass = RideClass.COMFORT,
                    paymentMethodId = "card-4417",
                ),
        )
}
