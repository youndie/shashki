package io.github.youndie.shashki.server.feature.quote

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.feature.route.data.NoRouteException
import io.github.youndie.shashki.server.pricing.RouteEstimator

/**
 * How long until a car of a class reaches the pickup.
 *
 * **The nearest candidate, routed.** Both halves already existed and were not joined: the geo-index
 * answers "who is near, of this class" (B-20) and the router answers "how long from here to there"
 * (B-23). The wait is the second applied to the first.
 *
 * **The nearest by straight line, not the fastest by road**, and that is a decision rather than an
 * oversight. Routing every candidate to find the quickest would be one graph search per online
 * driver per class for a number shown before anybody has ordered anything; the index's own ordering
 * is what the offer cascade already uses, so the rider is told about the driver they would actually
 * be offered.
 *
 * **`null` is an answer.** No candidate of that class is the kit's "no cars nearby". So is a
 * candidate the router cannot reach — a driver across the boundary of the city extract, where
 * B-23's own note says out-of-bbox and far-from-road are indistinguishable. Neither is a number, and
 * a number is what a screen would show as a promise.
 */
public class PickupEta(
    private val candidates: CandidateSource,
    private val estimator: RouteEstimator,
) {
    public fun waitFor(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): PickupWait? {
        val nearest = candidates.candidates(pickup, rideClass).firstOrNull() ?: return null
        return try {
            PickupWait(nearest.driverId, estimator.estimate(nearest.at, pickup).durationSeconds)
        } catch (_: NoRouteException) {
            null
        }
    }
}

/**
 * The wait, and whose it is (B-72).
 *
 * The driver's id travels with the seconds so the tile can name the car the wait was computed for —
 * the same candidate the cascade would offer first, not a model guessed from the class.
 */
public data class PickupWait(
    val driverId: String,
    val seconds: Int,
)
