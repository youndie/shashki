package io.github.youndie.shashki.protocol

import kotlinx.serialization.Serializable

/**
 * The states a ride passes through, and the only place they are written down.
 *
 * The order is the order of the saga: `petich` walks a ride through `ENRICHMENT → VALIDATION →
 * AUTHORIZATION → EXECUTION → POST_PROCESSING`, and these are what the rider and the driver see
 * while it does. Research §1.4 verified that the phase names above are the engine's own.
 *
 * `CANCELLED` is reachable from every state before `COMPLETED`, which is why it is not a step in
 * the sequence: a cancellation is compensation running backwards, not a further state forwards.
 */
@Serializable
public enum class RideStatus {
    REQUESTED,
    MATCHING,
    ASSIGNED,
    ARRIVING,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/** The service class a rider chooses, and the only axis pricing varies on today. */
@Serializable
public enum class RideClass {
    ECONOMY,
    COMFORT,
    BUSINESS,
}
