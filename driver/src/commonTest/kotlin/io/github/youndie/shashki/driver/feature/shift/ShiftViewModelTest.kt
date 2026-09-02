package io.github.youndie.shashki.driver.feature.shift

import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftUiAction
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftUiEvent
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftViewModel
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.RideClass
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val shift = FakeShiftRepository()
    private val offers = FakeOfferRepository()

    @BeforeTest
    fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    /** Going online is the socket taking reports, not the button changing colour. */
    @Test
    fun `going online holds the socket open and keeps sending`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(9.seconds)

            assertTrue(model.uiState.value.online)
            // One immediately, then one every four seconds: a driver who waited to become a
            // candidate would have been told a small lie by the button.
            assertEquals(3, shift.sent.size)
            assertEquals("driver-1", shift.sent.first().driverId)
            assertEquals(3, model.uiState.value.reported)
        }

    /** A socket that will not open must say so rather than leave a screen reading "waiting". */
    @Test
    fun `a socket that refuses to open reports and drops back offline`() =
        runTest(dispatcher) {
            shift.failWith = RuntimeException("connection refused")
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            val event = model.events.first()

            assertIs<ShiftUiEvent.Failed>(event)
            assertFalse(model.uiState.value.online)
        }

    /**
     * **The countdown counts the server's duration, not the device's clock.**
     *
     * The offer here expires at a server timestamp an hour away from anything this process would
     * call "now" — which is exactly the laptop-with-a-wrong-clock case. What the screen shows is
     * fifteen, because the server sent both ends of the interval and the client subtracted them
     * from each other.
     */
    @Test
    fun `the countdown starts at the seconds the server measured and ticks down`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 15, nowEpochMs = 4_000_000_000)
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            assertEquals(15, model.uiState.value.secondsLeft)
            assertEquals(15, model.uiState.value.secondsTotal)

            advanceTimeBy(4.seconds)

            assertEquals(11, model.uiState.value.secondsLeft)
            assertNotNull(model.uiState.value.offer)
        }

    /**
     * Zero drops the card, and the poll does not bring it straight back.
     *
     * **The second half is the one that was wrong.** The board does not go empty the instant the
     * countdown does — the withdrawal is the server's and the poll in flight still carries the old
     * answer — so the first version of this screen took the card down and put it up again two
     * seconds later, with a fresh fifteen on it. The test caught it because the fake board keeps
     * offering, which is exactly what a real one does for one more poll.
     */
    @Test
    fun `an offer that runs out of seconds leaves the screen`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 3)
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            advanceTimeBy(4.seconds)

            assertNull(model.uiState.value.offer)
            assertEquals(0, model.uiState.value.secondsLeft)

            // Three more polls' worth. The board still holds it; the screen is done with it.
            advanceTimeBy(7.seconds)
            assertNull(model.uiState.value.offer)
        }

    @Test
    fun `accepting sends this driver's decision and answers with the ride`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 15)
            val model = viewModel(backgroundScope)
            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)

            model.onAction(ShiftUiAction.Accept)
            advanceTimeBy(start)
            val event = model.events.first()

            assertEquals(DriverDecision.ACCEPT, offers.answered?.decision)
            assertEquals("driver-1", offers.answered?.driverId)
            assertIs<ShiftUiEvent.Accepted>(event)
            assertEquals("ride-1", event.rideId)
        }

    /**
     * B-29's second criterion, on the client's side of it.
     *
     * The tab was asleep; the cascade moved on; the server answers 409. **What must not happen is a
     * trip screen** — and the only thing standing between the driver and one is that the outcome is
     * read from the answer rather than assumed from the absence of an exception.
     */
    @Test
    fun `an offer that has gone to somebody else does not become a trip`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 15)
            offers.gone = true
            val model = viewModel(backgroundScope)
            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)

            model.onAction(ShiftUiAction.Accept)
            advanceTimeBy(start)
            val event = model.events.first()

            assertEquals(ShiftUiEvent.Gone, event)
            assertNull(model.uiState.value.offer)
            assertTrue(model.uiState.value.online, "the driver is still on shift and still a candidate")
        }

    /** Going offline closes the socket, which is how the server hears about it. */
    @Test
    fun `going offline stops the reports`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)
            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(5.seconds)
            val whileOnline = shift.sent.size

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(20.seconds)

            assertFalse(model.uiState.value.online)
            assertEquals(whileOnline, shift.sent.size)
        }

    /**
     * A nudge, not a wait.
     *
     * **`advanceUntilIdle` does not run the loops these tests are about.** The socket, the poll and
     * the countdown all live in `backgroundScope` — they have to, since none of them ends on its own
     * — and `advanceUntilIdle` deliberately ignores background work: it returned instantly and every
     * assertion about seconds read zero, which looks exactly like a countdown that never started.
     * Moving the virtual clock is what runs them, and one millisecond is enough to start what is
     * already scheduled without ticking anything.
     */
    private val start = 1.milliseconds

    private fun viewModel(scope: CoroutineScope) =
        ShiftViewModel(
            driverId = "driver-1",
            rideClass = RideClass.ECONOMY,
            rating = 4.9,
            at = FakeOfferRepository.PICKUP,
            goOnline = GoOnlineUseCase(shift),
            watchOffer = WatchOfferUseCase(offers),
            answerOffer = AnswerOfferUseCase(offers),
            loopScope = scope,
        )
}
