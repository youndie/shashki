package io.github.youndie.shashki.server.feature.ride.domain

import io.github.youndie.shashki.protocol.RideView

/**
 * Reads. The saga's row *is* the ride until the trip and the settlement give it a life the saga
 * does not cover (research §1.4c); a second table now would be a copy that drifts.
 */
public interface RideRepository {
    public suspend fun find(id: String): RideView?
}

public class RideNotFoundException(
    public val id: String,
) : RuntimeException("ride $id not found")

public class OfferNotFoundException(
    public val driverId: String,
) : RuntimeException("no offer for driver $driverId")

/**
 * The driver answered an offer that is no longer theirs.
 *
 * **The saga refuses this silently and correctly**, by resuspending for the driver who *is* being
 * asked — so without this the answer would be a 200 carrying somebody else's ride. A tab that went
 * to sleep for twenty seconds and woke up to press accept is the ordinary case, not the exotic one.
 */
public class OfferGoneException(
    public val rideId: String,
    public val driverId: String,
) : RuntimeException("offer for ride $rideId is no longer $driverId's")
