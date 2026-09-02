package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.rider.feature.ride.domain.RateRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ReadRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.TipRideUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedUiAction
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedUiEvent
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedUiState
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * R8's two actions, and the one thing that must not happen (B-44).
 *
 * **Nothing is sent until *done*.** Tapping a third star and then a fourth would otherwise be two
 * ratings, and the second collides with the first on the server — a rider rates a ride once. And a
 * tip that went out because somebody looked at the row is a charge nobody agreed to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishedViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val rides =
        FakeRideRepository(
            ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.COMPLETED, driverId = "driver-1"),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        FinishedViewModel(
            rideId = "ride-1",
            readRide = ReadRideUseCase(rides),
            rateRide = RateRideUseCase(rides),
            tipRide = TipRideUseCase(rides),
        )

    @Test
    fun `stars and a tip are chosen without anything being sent`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(FinishedUiAction.Stars(3))
            model.onAction(FinishedUiAction.Stars(4))
            model.onAction(FinishedUiAction.Tip(1))
            advanceUntilIdle()

            assertEquals(4, model.uiState.value.stars)
            assertEquals(1, model.uiState.value.selectedTip)
            assertNull(rides.rated, "a star sent a rating")
            assertNull(rides.tipped, "looking at the tips charged the card")
        }

    @Test
    fun `done sends the last rating and the chosen tip, once`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(FinishedUiAction.Stars(3))
            model.onAction(FinishedUiAction.Stars(5))
            model.onAction(FinishedUiAction.Tip(2))
            model.onAction(FinishedUiAction.Done)
            advanceUntilIdle()

            assertEquals(5, rides.rated)
            assertEquals(FinishedUiState.TIPS[2], rides.tipped)
            assertEquals(FinishedUiEvent.Done, model.events.first())
        }

    /** **Rating without a tip is the common case**, and *skip* costs nothing at all. */
    @Test
    fun `skip rates and charges nothing`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(FinishedUiAction.Stars(5))
            model.onAction(FinishedUiAction.Tip(null))
            model.onAction(FinishedUiAction.Done)
            advanceUntilIdle()

            assertEquals(5, rides.rated)
            assertNull(rides.tipped, "skip tipped anyway")
        }

    /** And a rider who says nothing at all leaves with nothing sent. */
    @Test
    fun `done with no stars and no tip sends neither`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(FinishedUiAction.Done)
            advanceUntilIdle()

            assertNull(rides.rated)
            assertNull(rides.tipped)
            assertEquals(FinishedUiEvent.Done, model.events.first())
        }

    /** The sum on the screen is the settlement's, and the state carries the ride that has it. */
    @Test
    fun `the ride behind the screen is the one that was charged`() =
        runTest(dispatcher) {
            rides.ride = rides.ride.copy(chargedCents = 724)
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(
                724,
                model.uiState.value.ride
                    ?.chargedCents,
            )
        }
}
