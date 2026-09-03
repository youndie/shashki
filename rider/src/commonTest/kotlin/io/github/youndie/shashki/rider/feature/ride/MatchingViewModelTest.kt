package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.SearchView
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingUiAction
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingUiEvent
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingUiState
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingViewModel
import io.github.youndie.shashki.rider.feature.ride.ui.cancelPrompt
import io.github.youndie.shashki.ui.screens.MatchingStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The wait, and the three ways out of it (B-43).
 *
 * **What is being tested is a distinction the server does not make.** `CANCELLED` is one status for
 * two events — the cascade ran out of drivers, and this rider pressed cancel — and only the client
 * knows which, because it is the one that pressed. Getting that wrong shows "no cars nearby" to
 * somebody who cancelled, which is a screen blaming the city for the rider's own decision.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val rides = FakeRideRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The poll never ends on its own, so it runs on a scope `runTest` does not wait for. */
    private fun TestScope.viewModel() =
        MatchingViewModel(
            rideId = "ride-1",
            observeRide = ObserveRideUseCase(rides),
            cancelRide = CancelRideUseCase(rides),
            loopScope = backgroundScope,
        )

    @Test
    fun `a ride nobody has taken yet is still looking`() =
        runTest(dispatcher) {
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(MatchingStage.LOOKING, model.uiState.value.stage)
            assertEquals(
                "ride-1",
                model.uiState.value.ride
                    ?.id,
            )
        }

    /**
     * The kit's `0:24`, counted from a duration the server handed over (B-73): the poll answers with
     * both ends of the deadline, the screen takes their difference once and then ticks.
     */
    @Test
    fun `the countdown is the server's duration, ticking`() =
        runTest(dispatcher) {
            rides.ride =
                rides.ride.copy(
                    status = RideStatus.MATCHING,
                    search =
                        SearchView(
                            carsNearby = 3,
                            asked = 1,
                            offerExpiresAtEpochMs = 1_015_000,
                            nowEpochMs = 1_000_000,
                        ),
                )
            val model = viewModel()

            advanceUntilIdle()
            assertEquals(15, model.uiState.value.secondsLeft, "fifteen seconds, from the two clocks the server sent")

            advanceTimeBy(3.seconds + 10.milliseconds)
            assertEquals(12, model.uiState.value.secondsLeft)
        }

    /** A driver said yes: the screen hands the ride to the trip rather than drawing a car itself. */
    @Test
    fun `a ride that gets a driver leaves for the trip`() =
        runTest(dispatcher) {
            rides.ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.ASSIGNED, driverId = "driver-1")
            val model = viewModel()

            advanceUntilIdle()
            val event = model.events.first()

            assertIs<MatchingUiEvent.Assigned>(event)
            assertEquals("ride-1", event.rideId)
        }

    /**
     * **B-43's second criterion, at the level a view model can hold it.** The server's part — every
     * candidate declining until the cascade runs out — is `SimulatedCascadeTest`'s; what arrives here
     * is the `CANCELLED` that ends it, and the screen has to read it as an empty city rather than as
     * a finished ride.
     */
    @Test
    fun `a cascade that ran out of drivers lands on no cars nearby`() =
        runTest(dispatcher) {
            rides.ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.CANCELLED)
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(MatchingStage.NO_CARS, model.uiState.value.stage)
            // And nothing is sent: leaving this screen is the rider's decision, through *try again*.
            assertNull(model.events.firstOrNullNow())
        }

    /** *try again* goes back to the picker, which still holds the address and the class. */
    @Test
    fun `try again asks to go back rather than ordering the same ride twice`() =
        runTest(dispatcher) {
            rides.ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.CANCELLED)
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(MatchingUiAction.Act)
            advanceUntilIdle()

            assertEquals(MatchingUiEvent.Back, model.events.first())
            assertNull(rides.requested, "try again ordered a second ride on its own")
        }

    /**
     * The rider's own cancellation, and the thing it must not turn into.
     *
     * The poll answers `CANCELLED` a moment later — the same status the empty city produces — and
     * the screen must not flip to "no cars nearby" on the way out.
     */
    @Test
    fun `cancelling asks first, then cancels, and never reads as no cars nearby`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(MatchingUiAction.Act)
            assertTrue(model.uiState.value.confirming, "cancel went straight through without asking")

            rides.ride = FakeRideRepository.REQUESTED.copy(status = RideStatus.CANCELLED)
            model.onAction(MatchingUiAction.ConfirmCancel)
            advanceUntilIdle()
            advanceTimeBy(5.seconds)
            advanceUntilIdle()

            assertEquals("ride-1", rides.cancelled)
            assertEquals(MatchingUiEvent.Back, model.events.first())
            assertEquals(MatchingStage.LOOKING, model.uiState.value.stage, "the rider's own cancel read as no cars")
        }

    /**
     * **The fee comes from the ride and the copy says the number.**
     *
     * The rule — a quarter of the fare once a driver has set off — lives in `Commission` on the
     * server, and `RideView.cancellationFeeCents` is what it produces. A client that multiplied the
     * fare itself would be a second copy of a pricing rule; this asserts that the amount shown is the
     * one that was sent, and that free and not-free are different sentences.
     */
    @Test
    fun `the confirmation shows the fee the server named`() {
        val free = cancelPrompt(FakeRideRepository.REQUESTED.copy(cancellationFeeCents = 0))
        val charged =
            cancelPrompt(
                FakeRideRepository.REQUESTED.copy(
                    status = RideStatus.ASSIGNED,
                    cancellationFeeCents = 724,
                ),
            )

        assertTrue("nothing" in free.message, free.message)
        assertTrue("$ 7.24" in charged.message, charged.message)
        assertEquals(free.title, charged.title, "the question changes with the number")
    }

    /** The prompt is a function of the ride, so a state with none is still answerable. */
    @Test
    fun `a confirmation with no ride yet is free rather than empty`() {
        assertTrue("nothing" in cancelPrompt(MatchingUiState().ride).message)
    }
}

/** The next event if there is one already, without waiting for one that may never come. */
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNullNow(): T? =
    kotlinx.coroutines.withTimeoutOrNull(1) { first() }
