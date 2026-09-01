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
