package io.github.youndie.shashki.driver.feature.trip

import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.feature.trip.domain.ReadTripSummaryUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.driver.feature.trip.ui.TripSummaryViewModel
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripSummaryView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * D5's one piece of behaviour: **the settlement is a saga and can be a moment behind the trip's
 * end**, so the screen asks again rather than showing "no payout" to a driver whose payout is being
 * written as they look (B-70).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripSummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val trips = FakeTrips()

    @Test
    fun `a payout that is a moment late is waited for, and formatted once it is in`() =
        runTest(dispatcher) {
            trips.answers = mutableListOf(null, null, SUMMARY)
            val model = TripSummaryViewModel("ride-1", useCase(), backgroundScope, retryAfter = RETRY)

            advanceTimeBy(TICK)
            assertTrue(model.uiState.value.loading, "two 404s in, still asking")
            assertNull(model.uiState.value.summary)

            advanceTimeBy(RETRY * 2 + TICK)
            val state = assertNotNull(model.uiState.value.summary)
            assertFalse(model.uiState.value.loading)
            assertEquals("trip complete", state.status)
            assertEquals("+$ 26.17", state.earned, "the share plus the tip, and nothing multiplied here")
            assertEquals("card-4417 · today $ 46.32", state.meta)
            assertEquals(
                listOf("fare" to "$ 28.96", "service fee 20 %" to "-$ 5.79", "tip" to "$ 3", "35 min · 22.8 km" to "—"),
                state.lines,
            )
            assertEquals(3, trips.asked, "asked exactly until it was answered")
        }

    /** D4·a is this screen with another first line: the fee's share as compensation (B-80). */
    @Test
    fun `a cancelled ride reads as compensation`() =
        runTest(dispatcher) {
            trips.answers =
                mutableListOf(
                    SUMMARY.copy(cancelled = true, payoutCents = 582, fareCents = 728, feeCents = 146, tipCents = 0),
                )
            val model = TripSummaryViewModel("ride-1", useCase(), backgroundScope, retryAfter = RETRY)

            advanceTimeBy(TICK)
            val state = assertNotNull(model.uiState.value.summary)

            assertEquals("passenger cancelled", state.status)
            assertEquals("+$ 5.82", state.earned)
            assertEquals("cancellation fee" to "$ 7.28", state.lines.first())
        }

    @Test
    fun `after the retries it says so rather than asking for ever`() =
        runTest(dispatcher) {
            trips.answers = mutableListOf()
            val model = TripSummaryViewModel("ride-1", useCase(), backgroundScope, retryAfter = RETRY)

            advanceTimeBy(RETRY * 10)

            assertFalse(model.uiState.value.loading)
            assertNull(model.uiState.value.summary)
            assertEquals(5, trips.asked)
        }

    private fun useCase() = ReadTripSummaryUseCase(trips, DriverIdentity { "driver-1" })

    private class FakeTrips : TripRepository {
        /** What each read answers, in order; `null` is the 404. Empty means 404 for ever. */
        var answers: MutableList<TripSummaryView?> = mutableListOf()
        var asked = 0

        override suspend fun read(rideId: String): RideView = error("not this screen's")

        override suspend fun advance(
            rideId: String,
            driverId: String,
            to: RideStatus,
        ): RideView = error("not this screen's")

        override suspend fun road(
            from: GeoPoint,
            to: GeoPoint,
        ): List<GeoPoint> = listOf(from, to)

        override suspend fun summary(
            rideId: String,
            driverId: String,
        ): TripSummaryView {
            asked++
            return answers.removeFirstOrNull() ?: error("ride $rideId has not been paid out yet")
        }
    }

    private companion object {
        val RETRY = 100.milliseconds
        val TICK = 10.milliseconds
        val SUMMARY =
            TripSummaryView(
                rideId = "ride-1",
                payoutCents = 2_317,
                fareCents = 2_896,
                feeCents = 579,
                feePercent = 20,
                tipCents = 300,
                currency = "USD",
                distanceMetres = 22_800,
                durationSeconds = 2_100,
                paymentMethodId = "card-4417",
                todayCents = 4_632,
            )
    }
}
