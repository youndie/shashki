package io.github.youndie.shashki.server.pricing

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.common.haversineMetres
import kotlin.math.roundToInt

/**
 * Distance, duration and the road between two points.
 *
 * **One interface rather than two.** B-23 needed geometry, which the saga does not, and the tempting
 * shape was a second `Router` beside this one. Two abstractions over the same question drift: the
 * quote would be priced on one road and the rider would watch the car drive along another. So the
 * geometry joins the estimate and the saga ignores the field it does not need.
 */
public interface RouteEstimator {
    public fun estimate(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteEstimate
}

public data class RouteEstimate(
    val distanceMetres: Int,
    val durationSeconds: Int,
    /**
     * The road, from [from] to [to] inclusive. Empty only from an estimator that does not know one —
     * which is to say from [StraightLineRouteEstimator], where a straight line between the two
     * points would be a road that is not there.
     */
    val geometry: List<GeoPoint> = emptyList(),
)

/**
 * Great-circle distance at a city speed. **A stand-in, and named as one**: research §1.4d builds the
 * saga against stubs so the saga can be killed at phase boundaries before matching or routing exist,
 * and B-23's own text calls this "the wrong number to put on a screen beside a price". It is the
 * right number to test a saga with, because a saga does not care whether the road bends.
 */
public class StraightLineRouteEstimator(
    private val speedMetresPerSecond: Double = CITY_SPEED_MPS,
) : RouteEstimator {
    override fun estimate(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteEstimate {
        val metres = haversineMetres(from, to)
        return RouteEstimate(metres.roundToInt(), (metres / speedMetresPerSecond).roundToInt())
    }

    private companion object {
        /** 30 km/h. A city average, and a hypothesis until B-23 routes on real roads. */
        const val CITY_SPEED_MPS = 30_000.0 / 3600
    }
}

/**
 * `base + per km + per minute`, times the class. The brief's formula, with a surge hook left as a
 * multiplier of 1 so the interface exists before the logic does.
 *
 * **Every number here is a hypothesis** — the brief says "base + per km + per minute, class
 * coefficients", not what they are. They are named constants so the day somebody measures what a
 * ride in the demo city costs, the change is one line and not a hunt.
 */
public class Pricing(
    private val currency: String = "USD",
    private val surge: (GeoPoint) -> Double = { 1.0 },
) {
    public fun quote(
        pickup: GeoPoint,
        rideClass: RideClass,
        estimate: RouteEstimate,
    ): Quote {
        val km = estimate.distanceMetres / 1000.0
        val minutes = estimate.durationSeconds / 60.0
        val raw = BASE_CENTS + PER_KM_CENTS * km + PER_MINUTE_CENTS * minutes
        val amount = raw * coefficient(rideClass) * surge(pickup)
        return Quote(
            distanceMetres = estimate.distanceMetres,
            durationSeconds = estimate.durationSeconds,
            amountCents = amount.roundToInt().toLong().coerceAtLeast(MINIMUM_CENTS),
            currency = currency,
        )
    }

    private fun coefficient(rideClass: RideClass): Double =
        when (rideClass) {
            RideClass.ECONOMY -> 1.0
            RideClass.COMFORT -> 1.5
            RideClass.BUSINESS -> 2.2
        }

    private companion object {
        const val BASE_CENTS = 150.0
        const val PER_KM_CENTS = 90.0
        const val PER_MINUTE_CENTS = 20.0
        const val MINIMUM_CENTS = 300L
    }
}
