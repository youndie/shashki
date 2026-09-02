package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingUiState
import io.github.youndie.shashki.rider.feature.ride.ui.headline
import io.github.youndie.shashki.ui.screens.MatchingStage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the rider is told when the search ends (B-58).
 *
 * **The client used to assert the reason.** Every ended search said "no cars nearby", whatever the
 * server had refused it for — which was safe only because the field carrying the real answer was
 * written by nobody. It is written now, so the screen can stop guessing.
 */
class MatchingHeadlineTest {
    @Test
    fun `while looking, the headline is the search`() {
        assertEquals("looking for a car", state(MatchingStage.LOOKING, reason = null).headline())
    }

    @Test
    fun `the server's reason is what the screen says`() {
        assertEquals("no cars nearby", state(MatchingStage.NO_CARS, "no cars nearby").headline())
        assertEquals(
            "the card was declined",
            state(MatchingStage.NO_CARS, "the card was declined").headline(),
            "the client printed its own reason over the server's",
        )
    }

    /** A server that says nothing is, in this product, a cascade that ran out — the kit's R5·a. */
    @Test
    fun `with no reason at all the kit's own words stand`() {
        assertEquals("no cars nearby", state(MatchingStage.NO_CARS, reason = null).headline())
    }

    private fun state(
        stage: MatchingStage,
        reason: String?,
    ) = MatchingUiState(
        stage = stage,
        ride =
            RideView(
                id = "ride-1",
                status = RideStatus.CANCELLED,
                rideClass = RideClass.ECONOMY,
                pickup = GeoPoint(46.05, 14.50),
                dropoff = GeoPoint(46.06, 14.53),
                cancellationReason = reason,
            ),
    )
}
