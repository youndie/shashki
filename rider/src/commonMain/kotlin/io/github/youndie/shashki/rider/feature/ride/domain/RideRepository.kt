package io.github.youndie.shashki.rider.feature.ride.domain

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.RouteView

/**
 * What the rider application asks the server for.
 *
 * Suspend and remote-only: there is no local cache and no single source of truth to keep, so these
 * are direct calls rather than flows. A `Flow` here would promise a subscription the server does not
 * offer — the ride is polled, and [ObserveRideUseCase] is where that lives, in the open.
 */
public interface RideRepository {
    public suspend fun quotes(
        from: GeoPoint,
        to: GeoPoint,
    ): QuotesView

    public suspend fun request(request: RideRequest): RideView

    public suspend fun read(rideId: String): RideView

    /** The rider's own rides, newest first — R9's list (B-45). */
    public suspend fun mine(): List<RideView>

    public suspend fun cancel(rideId: String)

    /** R8's first half: one to five, once, and only after the ride is over (B-44). */
    public suspend fun rate(
        rideId: String,
        stars: Int,
    )

    /** R8's second half. Cents, because the server must not have to agree about what 10% is. */
    public suspend fun tip(
        rideId: String,
        amountCents: Long,
    )

    /** The road between two points, for the line the rider watches the car travel along. */
    public suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteView

    /** Where the assigned car is, or a driver with no position when their phone has gone quiet. */
    public suspend fun driver(rideId: String): AssignedDriverView
}
