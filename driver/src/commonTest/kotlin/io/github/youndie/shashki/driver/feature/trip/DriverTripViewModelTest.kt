package io.github.youndie.shashki.driver.feature.trip

import io.github.youndie.shashki.driver.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripUiAction
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripUiEvent
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripViewModel
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * The driver's one button, which B-29 could not draw because the server had no route behind it.
 *
 * What these hold is the rule the screen must not break: **the state comes from the answer, never
 * from the intention.** The request that moves a trip to `COMPLETED` is the one that captures the
 * rider's money, so a screen that advanced optimistically would show a finished ride whenever the
 * network hiccuped — and the driver would stop driving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverTripViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val trips = FakeTrips()

    @BeforeTest
    fun main() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the button asks for the next state and the screen shows what came back`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)
            advanceTimeBy(START)
            assertEquals(RideStatus.ARRIVING, model.uiState.value.next, "assigned, so the next step is setting off")

            model.onAction(DriverTripUiAction.Advance)
            advanceTimeBy(START)

            assertEquals(RideStatus.ARRIVING to "driver-1", trips.asked)
            assertEquals(
                RideStatus.ARRIVING,
                model.uiState.value.ride
                    ?.status,
            )
            assertEquals(RideStatus.ARRIVED, model.uiState.value.next)
        }

    /** A refusal leaves the trip where it was and says so, rather than moving on regardless. */
    @Test
    fun `a refused transition does not move the screen`() =
        runTest(dispatcher) {
            trips.failAdvanceWith = RuntimeException("a trip goes ASSIGNED → ARRIVING, not ASSIGNED → COMPLETED")
            val model = viewModel(backgroundScope)
            advanceTimeBy(START)

            model.onAction(DriverTripUiAction.Advance)
            advanceTimeBy(START)
            val event = model.events.first()

            assertIs<DriverTripUiEvent.Failed>(event)
            assertEquals(
                RideStatus.ASSIGNED,
                model.uiState.value.ride
                    ?.status,
            )
            assertFalse(model.uiState.value.advancing, "the button stayed disabled after a refusal")
        }

    /** The last press ends the ride, which is what the shell navigates away from. */
    @Test
    fun `finishing the trip reports it once`() =
        runTest(dispatcher) {
            trips.ride = RIDE.copy(status = RideStatus.IN_PROGRESS)
            val model = viewModel(backgroundScope)
            advanceTimeBy(START)
            assertEquals(RideStatus.COMPLETED, model.uiState.value.next)

            model.onAction(DriverTripUiAction.Advance)
            advanceTimeBy(START)
            val event = model.events.first()

            assertEquals(DriverTripUiEvent.Finished, event)
            assertEquals(
                RideStatus.COMPLETED,
                model.uiState.value.ride
                    ?.status,
            )
            assertNull(model.uiState.value.next, "a finished ride still offers a button")
        }

    private class FakeTrips : TripRepository {
        var ride: RideView = RIDE
        var asked: Pair<RideStatus, String>? = null
        var failAdvanceWith: Throwable? = null

        override suspend fun read(rideId: String): RideView = ride

        override suspend fun advance(
            rideId: String,
            driverId: String,
            to: RideStatus,
        ): RideView {
            asked = to to driverId
            failAdvanceWith?.let { throw it }
            ride = ride.copy(status = to)
            return ride
        }
    }

    private fun viewModel(scope: CoroutineScope) =
        DriverTripViewModel(
            rideId = "ride-1",
            driverId = "driver-1",
            observeTrip = ObserveTripUseCase(trips),
            advanceTrip = AdvanceTripUseCase(trips),
            loopScope = scope,
        )

    private companion object {
        /** A nudge of the virtual clock: the poll lives in `backgroundScope`, which idling ignores. */
        val START = 1.milliseconds

        val RIDE =
            RideView(
                id = "ride-1",
                status = RideStatus.ASSIGNED,
                rideClass = RideClass.ECONOMY,
                pickup = GeoPoint(46.0511, 14.5051),
                dropoff = GeoPoint(46.2237, 14.4576),
                quote = Quote(22_806, 2_079, 2_490, "USD"),
                driverId = "driver-1",
            )
    }
}
