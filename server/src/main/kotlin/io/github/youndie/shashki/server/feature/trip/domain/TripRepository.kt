package io.github.youndie.shashki.server.feature.trip.domain

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.TripProgression

/** Where a ride has got to after a driver took it. */
public data class Trip(
    val rideId: String,
    val driverId: String,
    val status: RideStatus,
)

/**
 * The trip, which is a row rather than a saga.
 *
 * **Research §1.4c is the whole design of this interface.** `ARRIVING → ARRIVED → IN_PROGRESS →
 * COMPLETED` is driven by the driver's own transitions and by location, and *nothing in it needs
 * compensating* — there is no money to unwind and no reservation to give back, because the order
 * saga already settled both. A saga here would be five phases of ceremony around a status column.
 *
 * The row appears when the driver first says the trip has moved, not when the order saga finishes.
 * Creating it inside the saga would put a side effect with no compensation in a saga step, and the
 * absence of a row is a perfectly good way of saying "assigned, and not started yet".
 */
public interface TripRepository {
    public fun find(rideId: String): Trip?

    /** Writes the driver's new state. The caller has already decided the transition is legal. */
    public fun advance(trip: Trip)
}

/** The driver answering about a ride that is not theirs, or is not there. */
public class NotThisDriversRideException(
    public val rideId: String,
    public val driverId: String,
) : RuntimeException("ride $rideId is not $driverId's to advance")

/** A transition that is not the next one. */
public class OutOfOrderTransitionException(
    public val from: RideStatus,
    public val to: RideStatus,
) : RuntimeException("a trip goes $from → ${TripProgression.next(from)}, not $from → $to")
