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
        assertTrue(offer.available)
    }

    private fun state(etaSeconds: Int?) =
        ClassPickerUiState(
            quotes =
                listOf(
                    ClassQuote(
                        rideClass = RideClass.ECONOMY,
                        quote = Quote(22_806, 2_079, 2_896, "USD"),
                        pickupEtaSeconds = etaSeconds,
                    ),
                ),
        )
}
