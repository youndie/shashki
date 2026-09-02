package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedUiState
import io.github.youndie.shashki.rider.feature.ride.ui.totalWithTipCents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * What R8 knows before it asks anything (B-59).
 *
 * **The screen used to know nothing.** It drew five empty stars over a ride that carried a five, so
 * a refresh invited a second rating of the same journey; and `selectedTip == null` meant *skip*, so
 * the one accent surface the kit allows a screen was spent, on opening, recommending that nothing be
 * paid.
 */
class FinishedReadsBackTest {
    @Test
    fun `nothing is chosen when the screen opens`() {
        val state = FinishedUiState(ride = rated(stars = null))

        assertNull(state.selectedTip)
        assertFalse(state.skipped, "the screen opened with the refusal filled in")
        assertNull(state.totalWithTipCents(), "a total appeared before anybody chose a tip")
    }

    @Test
    fun `a tip adds up with what was charged`() {
        val state = FinishedUiState(ride = rated(stars = null), selectedTip = 1)

        assertEquals(648L + FinishedUiState.TIPS[1], state.totalWithTipCents())
    }

    /** Skipping is a choice, and a choice with no number attached to it. */
    @Test
    fun `skipping is chosen and adds nothing`() {
        val state = FinishedUiState(ride = rated(stars = null), selectedTip = null, skipped = true)

        assertNull(state.totalWithTipCents())
    }

    private fun rated(stars: Int?) =
        RideView(
            id = "ride-1",
            status = RideStatus.COMPLETED,
            rideClass = RideClass.ECONOMY,
            pickup = GeoPoint(46.05, 14.50),
            dropoff = GeoPoint(46.06, 14.53),
            quote = Quote(4098, 389, 648, "USD"),
            chargedCents = 648,
            stars = stars,
            paymentMethodId = "card-4417",
        )
}
