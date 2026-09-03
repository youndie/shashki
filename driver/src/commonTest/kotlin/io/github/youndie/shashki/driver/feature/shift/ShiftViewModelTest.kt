package io.github.youndie.shashki.driver.feature.shift

import io.github.youndie.shashki.driver.feature.earnings.domain.EarningsRepository
import io.github.youndie.shashki.driver.feature.earnings.domain.ReadEarningsUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.shift.data.DevicePositionFixes
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.PositionSource
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftUiAction
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftUiEvent
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftViewModel
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.EarningsView
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    /**
     * The device's own positions, which this test controls (B-49).
     *
     * A shared flow that nobody emits into is exactly the shape of a denied permission, a desktop
     * window, and a browser with no geolocation — the three cases the fallback exists for, and the
     * default state of every test above.
     */
    private val device = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 4)

    /**
     * **A board that cannot be read is not an empty board** (B-64).
     *
     * The two were one screen: `runCatching { … }.getOrNull()` turned every failure into *no offer*,
     * so a client that could not parse what the server sent showed a quiet shift while the server
     * waited for an answer. Nobody could have told the difference from the outside, and on a running
     * stand nobody did.
     */
    @Test
    fun `a board that cannot be read says so and keeps the card that is up`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 15)
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            assertNotNull(model.uiState.value.offer)
            assertNull(model.uiState.value.boardUnreachable, "a working board reported trouble")

            offers.unreadable = IllegalStateException("Serializer for class 'OfferView' is not found")
            advanceTimeBy(3.seconds)

            assertEquals(
                "Serializer for class 'OfferView' is not found",
                model.uiState.value.boardUnreachable,
                "a failed poll was drawn as an empty board",
            )
            assertNotNull(model.uiState.value.offer, "a poll this client could not make took the card down")
        }

    /**
     * **A second offer, after a first one ran out** (B-64).
     *
     * This is the sequence the running stand was in when the card never appeared: an offer expired,
     * the board went empty, and the next ride was offered to the same driver. `finished` remembers
     * the ride whose card was taken down so that a poll already in flight cannot put it straight
     * back up — and if it were not cleared when the board goes empty, it would suppress every
     * offer after the first one for the rest of the shift.
     */
    @Test
    fun `an offer after an expired one still draws`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 3, rideId = "ride-first")
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            assertNotNull(model.uiState.value.offer, "the first offer never drew")

            advanceTimeBy(4.seconds)
            assertNull(model.uiState.value.offer, "the first offer outlived its seconds")

            // The board goes empty, which is the server agreeing that the offer is gone.
            offers.offer = null
            advanceTimeBy(4.seconds)

            offers.offer = FakeOfferRepository.offer(seconds = 15, rideId = "ride-second")
            advanceTimeBy(4.seconds)

            val second = model.uiState.value.offer
            assertNotNull(second, "the second offer was suppressed by the first one's memory")
            assertEquals("ride-second", second.rideId)
        }

    /**
     * **An offer with no time left must not vanish silently** (B-64).
     *
     * If the server's two clocks arrive equal — a slow response, a rounded value — the countdown has
     * nothing to count and `clearOffer` runs at once. The card would appear for one frame and be
     * remembered as finished, so the same offer is then ignored for as long as it is on the board:
     * a driver sees a shift with nothing in it while the server waits for an answer.
     */
    @Test
    fun `an offer that arrives with no seconds left is not remembered as answered`() =
        runTest(dispatcher) {
            offers.offer = FakeOfferRepository.offer(seconds = 0, rideId = "ride-late")
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)

            // Whatever the screen does with it, the offer is still the server's open question: the
            // next poll carries the same ride and the screen has to be able to show it.
            advanceTimeBy(3.seconds)
            assertNotNull(
                model.uiState.value.offer,
                "an offer with no seconds left was taken down and then suppressed for ever",
            )
        }

    /**
     * **The count is what the socket took, and a server that takes nothing must move it** (B-54).
     *
     * This is the state the running stand was in for the whole of B-53: the socket was open, frames
     * were going out every four seconds, the server discarded every one of them, and the screen read
     * `19 positions sent · waiting`. A count of what the client *wrote* cannot say anything about
     * that, which makes it a count of the application's own intentions — and the screen already has
     * one of those, the word *waiting*.
     */
    @Test
    fun `frames the server refuses do not move the count`() =
        runTest(dispatcher) {
            shift.accepting = false
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(9.seconds)

            assertEquals(3, shift.sent.size, "the client stopped sending")
            assertEquals(0, model.uiState.value.reported, "the screen counted frames nobody took")
        }

    /**
     * **A parked driver is a fact and not a bug**, and the screen has to be able to say so.
     *
     * With nothing granting a position the shift keeps sending — a fallback that stopped reporting
     * would take a driver off the map over a permission they are entitled to withhold — and the
     * source stays `CONFIGURED` for every one of those reports.
     */
    @Test
    fun `with no device position the shift keeps sending the configured point and says so`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(9.seconds)

            assertEquals(3, shift.sent.size)
            assertTrue(shift.sent.all { it.at == FakeOfferRepository.PICKUP }, "a position was invented")
            assertEquals(PositionSource.CONFIGURED, model.uiState.value.positionSource)
        }

    /**
     * A fix from the device replaces the configured point and the label follows it.
     *
     * **The cadence stays this bundle's.** Four positions arrive between two ticks and the socket
     * takes one report, carrying the newest of them: `watchPosition` fires when a phone decides it
     * has something to say, and the server's index wants a report every four seconds rather than
     * every time a car moves.
     */
    @Test
    fun `a device fix takes over and the screen names it`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(start)
            repeat(4) { step -> device.emit(GeoPoint(46.06 + step / 1000.0, 14.51)) }
            advanceTimeBy(4.seconds)

            assertEquals(2, shift.sent.size, "the device set the cadence")
            assertEquals(FakeOfferRepository.PICKUP, shift.sent.first().at, "the first report waited for a permission")
            assertEquals(GeoPoint(46.063, 14.51), shift.sent.last().at, "the newest fix is not what went out")
            assertEquals(PositionSource.DEVICE, model.uiState.value.positionSource)
        }

    private fun viewModel(scope: CoroutineScope) =
        ShiftViewModel(
            identity = { "driver-1" },
            rideClass = RideClass.ECONOMY,
            rating = 4.9,
            at = FakeOfferRepository.PICKUP,
            goOnline = GoOnlineUseCase(shift, DevicePositionFixes { device }),
            watchOffer = WatchOfferUseCase(offers),
            answerOffer = AnswerOfferUseCase(offers),
            readEarnings = ReadEarningsUseCase(FixedEarnings(EARNINGS)),
            now = { clock },
            loopScope = scope,
        )

    /** The kit's D2 tiles need a clock and a sum (B-81): the meter counts, the read answers once a minute. */
    @Test
    fun `online, the shift counts its hours and reads today's takings`() =
        runTest(dispatcher) {
            val model = viewModel(backgroundScope)
            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(1.seconds)

            assertEquals(EARNINGS, model.uiState.value.earnings, "read when the shift started")
            clock += 65_000
            advanceTimeBy(65.seconds)
            assertEquals(65, model.uiState.value.onlineForSeconds)

            model.onAction(ShiftUiAction.ToggleOnline)
            advanceTimeBy(1.seconds)
            assertEquals(null, model.uiState.value.onlineForSeconds, "offline, there are no hours to show")
        }

    private class FixedEarnings(
        private val view: EarningsView,
    ) : EarningsRepository {
        override suspend fun earnings(): EarningsView = view
    }

    private companion object {
        val EARNINGS =
            EarningsView(4_632, 4_632, 4_632, "USD", todayTrips = 2, weekTrips = 2, allTimeTrips = 2, rating = 4.9)
    }
}

/** The test's own clock: moved by hand where a test needs the hours to pass. */
private var clock: Long = 1_788_390_000_000L
