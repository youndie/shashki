package io.github.youndie.shashki.server.feature.ride.domain

import io.github.youndie.shashki.protocol.RideView

/**
 * Reads. The saga's row *is* the ride until the trip and the settlement give it a life the saga
 * does not cover (research §1.4c); a second table now would be a copy that drifts.
 */
public interface RideRepository {
    /**
     * The rides that belong to this address, newest first — R9's list (B-45).
     *
     * `null` means "no provider is configured", which is not the same as "nobody": see the
     * implementation, where the demo's single rider is the honest reading of it.
     */
    public suspend fun mine(riderEmail: String?): List<RideView> = emptyList()

    public suspend fun find(id: String): RideView?

    /**
     * Write down why a ride was refused, so the rider can be told (B-58).
     *
     * **The saga cannot do this itself, and that is a fact about petich rather than an oversight
     * here.** `InterceptorResult.Reject` and `Compensate` each take a reason, and neither the row nor
     * any of petich's own types carries it afterwards: only `Proceed`, `Suspend` and `Resuspend` take
     * an `EnrichedPayload`, and a step that is refusing returns none of those. So `Enriched.REJECTION`
     * — read by the projection since it was written — had nobody to write it, and every cancelled
     * ride came back with `cancellationReason: null`.
     *
     * It is written from outside the engine, at the two moments the answer is known and the engine
     * is not holding the row: after `process` returns, and when the cascade gives up.
     */
    public suspend fun recordRejection(
        rideId: String,
        reason: String,
    ) {
    }
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
