package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.rider.feature.ride.domain.RideRepository

/**
 * The server, as a hand-written fake.
 *
 * **No mocking library**, per the project's own rule: an object with the four methods overridden is
 * cheaper than a dependency, and it is the thing that makes a view-model test readable — what the
 * server said is written where the test can see it.
 */
internal class FakeRideRepository(
    var quotes: QuotesView = QUOTES,
    var ride: RideView = REQUESTED,
    var driver: AssignedDriverView = AssignedDriverView("driver-1", GeoPoint(46.05, 14.51)),
    /** The road `route` answers with; three points by default, so a test can split it at the car (B-77). */
    var road: List<GeoPoint> = listOf(GeoPoint(46.0511, 14.5051), GeoPoint(46.10, 14.48), GeoPoint(46.2237, 14.4576)),
    var failWith: Throwable? = null,
) : RideRepository {
    var requested: RideRequest? = null
    var cancelled: String? = null
    var rated: Int? = null
    var tipped: Long? = null
    var reads: Int = 0
    var history: List<RideView> = emptyList()

    override suspend fun quotes(
        from: GeoPoint,
        to: GeoPoint,
    ): QuotesView = failWith?.let { throw it } ?: quotes

    override suspend fun request(request: RideRequest): RideView {
        failWith?.let { throw it }
        requested = request
        return ride
    }

    override suspend fun read(rideId: String): RideView {
        reads++
        return failWith?.let { throw it } ?: ride
    }

    override suspend fun mine(): List<RideView> = failWith?.let { throw it } ?: history

    override suspend fun cancel(rideId: String) {
        cancelled = rideId
    }

    override suspend fun rate(
        rideId: String,
        stars: Int,
    ) {
        rated = stars
    }

    override suspend fun tip(
        rideId: String,
        amountCents: Long,
    ) {
        tipped = amountCents
    }

    override suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteView = RouteView(distanceMetres = 22_806, durationSeconds = 2_079, geometry = road)

    override suspend fun driver(rideId: String): AssignedDriverView = driver

    companion object {
        val QUOTES =
            QuotesView(
                distanceMetres = 22_806,
                durationSeconds = 2_079,
                classes =
                    listOf(
                        ClassQuote(RideClass.ECONOMY, Quote(22_806, 2_079, 2_490, "USD"), pickupEtaSeconds = 240),
                        ClassQuote(RideClass.COMFORT, Quote(22_806, 2_079, 3_890, "USD"), pickupEtaSeconds = 360),
                    ),
            )

        val REQUESTED =
            RideView(
                id = "ride-1",
                status = RideStatus.MATCHING,
                rideClass = RideClass.ECONOMY,
                pickup = GeoPoint(46.0511, 14.5051),
                dropoff = GeoPoint(46.2237, 14.4576),
                quote = Quote(22_806, 2_079, 2_490, "USD"),
            )
    }
}
