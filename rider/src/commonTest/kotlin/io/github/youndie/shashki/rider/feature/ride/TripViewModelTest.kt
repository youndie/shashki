package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.LegTarget
import io.github.youndie.shashki.protocol.LegView
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.WatchDriverUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.TripUiAction
import io.github.youndie.shashki.rider.feature.ride.ui.TripUiEvent
import io.github.youndie.shashki.rider.feature.ride.ui.TripViewModel
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.screens.TripStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rides = FakeRideRepository()

    @BeforeTest
    fun main() {
        Dispatchers.setMain(dispatcher)
        clock = NOW
    }

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    /** The three the screen has are the three the trip has; everything earlier is still an order. */
    @Test
    fun `the ride's status becomes the stage the screen shows`() =
        runTest(dispatcher) {
            for (
            (status, stage) in
            listOf(
                RideStatus.ASSIGNED to TripStage.ARRIVING,
                RideStatus.ARRIVING to TripStage.ARRIVING,
                RideStatus.ARRIVED to TripStage.ARRIVED,
                RideStatus.IN_PROGRESS to TripStage.IN_PROGRESS,
            )
            ) {
                val repository = FakeRideRepository(ride = FakeRideRepository.REQUESTED.copy(status = status))
                val model = viewModel(repository)

                settle()

                assertEquals(stage, model.uiState.value.stage, "$status should read as $stage")
            }
        }

    /**
     * **The scene is built from what the server said, and this is what proves it.** Pins at the
     * ride's own two points, a road between them, and the car where the driver endpoint put it — a
     * screen that drew a fixture regardless would pass every other assertion here.
     */
    @Test
    fun `the scene carries the ride's pins, its road and the car`() =
        runTest(dispatcher) {
            val model = viewModel(rides)

            settle()

            val scene = model.uiState.value.scene
            assertEquals(2, scene.pins.size)
            assertEquals(setOf(MapPin.Kind.PICKUP, MapPin.Kind.DROPOFF), scene.pins.map { it.kind }.toSet())
            assertEquals(FakeRideRepository.REQUESTED.pickup, scene.pins.first { it.kind == MapPin.Kind.PICKUP }.at)
            assertTrue(scene.route!!.ahead.isNotEmpty(), "no road for the rider to watch the car along")
            assertEquals(GeoPoint(46.05, 14.51), scene.cars.single().at)
        }

    /**
     * **A quiet phone becomes a band after half a minute, and not before** (B-80). Ten seconds in a
     * tunnel is not a lost car; forty is the kit's R7·a, and the number on it is how long.
     */
    @Test
    fun `a car quiet for long enough says so, with the seconds`() =
        runTest(dispatcher) {
            val repository = FakeRideRepository()
            val model = viewModel(repository)
            settle()

            repository.driver = AssignedDriverView(driverId = "driver-1", at = null)
            settle()
            assertNull(model.uiState.value.quietForSeconds, "a few seconds of silence is not a lost car")

            clock += 40_000
            settle()
            assertEquals(40, model.uiState.value.quietForSeconds)

            repository.driver = AssignedDriverView(driverId = "driver-1", at = GeoPoint(46.05, 14.51))
            settle()
            assertNull(model.uiState.value.quietForSeconds, "a position takes the band down")
        }

    /**
     * **Progress is colour, not thickness** (B-77): once the trip is running, the road behind the car
     * is the travelled phase and the road ahead the accent, split where the car is.
     */
    @Test
    fun `on the trip the road splits at the car`() =
        runTest(dispatcher) {
            val repository =
                FakeRideRepository(
                    ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.IN_PROGRESS),
                    road = listOf(GeoPoint(46.00, 14.50), GeoPoint(46.05, 14.51), GeoPoint(46.10, 14.52)),
                    driver = AssignedDriverView("driver-1", GeoPoint(46.0501, 14.5101)),
                )
            val model = viewModel(repository)

            settle()

            val route = assertNotNull(model.uiState.value.scene.route)
            assertEquals(
                listOf(GeoPoint(46.00, 14.50), GeoPoint(46.05, 14.51), GeoPoint(46.0501, 14.5101)),
                route.travelled,
            )
            assertEquals(
                listOf(GeoPoint(46.0501, 14.5101), GeoPoint(46.05, 14.51), GeoPoint(46.10, 14.52)),
                route.ahead,
            )
        }

    /** The kit's `arriving 20:06`: the leg's seconds from the rider's own clock, as a wall-clock time. */
    @Test
    fun `the arrival is a clock, from the leg to the drop-off`() =
        runTest(dispatcher) {
            val repository =
                FakeRideRepository(
                    ride =
                        FakeRideRepository.REQUESTED.copy(
                            status = RideStatus.IN_PROGRESS,
                            leg = LegView(LegTarget.DROPOFF, 11_200, 1_080),
                        ),
                )
            val model = viewModel(repository)

            settle()

            val expected =
                Instant
                    .fromEpochMilliseconds(NOW + 1_080_000)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
            assertEquals(expected, model.uiState.value.arrivingAt)
        }

    /**
     * A driver whose phone has gone quiet leaves the car where it was.
     *
     * Removing the marker would be the screen asserting the car had vanished, which is a stronger
     * claim than "we have not heard from it for three seconds" and a worse one to make.
     */
    @Test
    fun `a driver with no position does not remove the car`() =
        runTest(dispatcher) {
            val model = viewModel(rides)
            settle()
            val before =
                model.uiState.value.scene.cars
                    .single()

            rides.driver = AssignedDriverView(driverId = "driver-1", at = null)
            settle()

            assertEquals(
                before,
                model.uiState.value.scene.cars
                    .single(),
            )
        }

    @Test
    fun `a finished ride says so once and stops asking`() =
        runTest(dispatcher) {
            val repository = FakeRideRepository(ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.COMPLETED))
            val model = viewModel(repository)

            settle()
            val event = model.events.first()
            val readsWhenFinished = repository.reads
            settle()

            assertIs<TripUiEvent.Finished>(event)
            assertEquals(readsWhenFinished, repository.reads, "it went on polling a ride that had ended")
        }

    @Test
    fun `cancelling asks first and then reaches the server for this ride and no other`() =
        runTest(dispatcher) {
            val model = viewModel(rides)
            settle()

            // **Asking is the first half** (B-43). From this screen a driver has set off, so
            // cancelling settles a fee — the one place in this product where a tap moves money, and
            // the amount is on the confirmation before the button.
            model.onAction(TripUiAction.Cancel)
            settle()
            assertTrue(model.uiState.value.confirming, "the trip was cancelled without asking")
            assertNull(rides.cancelled, "the question reached the server on its own")

            model.onAction(TripUiAction.ConfirmCancel)
            settle()

            assertEquals("ride-1", rides.cancelled)
        }

    /** And the way out of the question is not the way through it. */
    @Test
    fun `dismissing the confirmation leaves the ride alone`() =
        runTest(dispatcher) {
            val model = viewModel(rides)
            settle()

            model.onAction(TripUiAction.Cancel)
            model.onAction(TripUiAction.DismissConfirm)
            settle()

            assertNull(rides.cancelled)
            assertTrue(!model.uiState.value.confirming)
        }

    /**
     * **A bounded advance, not `advanceUntilIdle`.**
     *
     * `WatchDriverUseCase` polls for as long as the screen is open and never terminates on its own,
     * which is right — a car keeps moving — and it means `advanceUntilIdle` would run virtual time
     * for ever. This lets both loops take a few turns and stops.
     */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private fun TestScope.viewModel(repository: FakeRideRepository) =
        TripViewModel(
            rideId = "ride-1",
            observeRide = ObserveRideUseCase(repository),
            watchDriver = WatchDriverUseCase(repository),
            cancelRide = CancelRideUseCase(repository),
            // Held still, so "arriving at" is a number a test can name (B-77) — and moved by hand
            // for the silence R7·a counts (B-80).
            now = { clock },
            // The screen's lifetime, in a test that has no screen.
            loopScope = backgroundScope,
        )

    private companion object {
        /** Longer than the driver poll and the ride poll, short enough to stay a handful of turns. */
        const val SETTLE_MILLIS = 4_000L
    }
}

/** A Tuesday in September, held still. */
private const val NOW = 1_788_390_000_000L

/** The tests' own clock: starts at [NOW] and moves only when a test says so. */
private var clock: Long = NOW
