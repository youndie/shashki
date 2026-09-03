package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerUiState
import io.github.youndie.shashki.rider.feature.ride.ui.offerFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a tile says about a class nobody is driving (B-62).
 *
 * **A price beside "no cars nearby" is an offer the product cannot honour.** The kit's tile has a
 * state for exactly this — the em dash where the figure goes — and the screen was handing it a
 * figure anyway, so on a stand with nobody online all three classes read `no cars nearby` over a
 * price, and the bar underneath offered to order the first of them.
 */
class ClassOfferTest {
    @Test
    fun `a class with no candidate has no price`() {
        val offer = state(etaSeconds = null).offerFor(RideClass.ECONOMY)

        assertEquals("no cars nearby", offer.meta)
        assertNull(offer.price, "a class nobody is driving was priced")
        assertFalse(offer.available)
    }

    @Test
    fun `a class with a car keeps its price and its wait`() {
        val offer = state(etaSeconds = 240).offerFor(RideClass.ECONOMY)

        assertEquals("$ 28.96", offer.price)
        assertEquals("4 min", offer.meta)
        assertTrue(offer.available)
    }

    /** The kit's `4 min · Kia Rio`, from the driver record the wait was routed for (B-72). */
    @Test
    fun `the tile names the car the wait was computed for`() {
        val offer = state(etaSeconds = 240, car = "Skoda Octavia · white").offerFor(RideClass.ECONOMY)

        assertEquals("4 min · Skoda Octavia · white", offer.meta)
    }

    /** A car at the kerb is *here*, not `0 min` — the stand's own driver parks at the pickup (B-72). */
    @Test
    fun `a car at the kerb is here rather than nought minutes`() {
        assertEquals(
            "here · Skoda Octavia · white",
            state(etaSeconds = 0, car = "Skoda Octavia · white").offerFor(RideClass.ECONOMY).meta,
        )
        assertEquals("here", state(etaSeconds = 20).offerFor(RideClass.ECONOMY).meta)
        assertEquals(
            "1 min",
            state(etaSeconds = 40).offerFor(RideClass.ECONOMY).meta,
            "past the kerb it is minutes, rounded up",
        )
    }

    private fun state(
        etaSeconds: Int?,
        car: String? = null,
    ) = ClassPickerUiState(
        quotes =
            listOf(
                ClassQuote(
                    rideClass = RideClass.ECONOMY,
                    quote = Quote(22_806, 2_079, 2_896, "USD"),
                    pickupEtaSeconds = etaSeconds,
                    car = car,
                ),
            ),
    )
}
