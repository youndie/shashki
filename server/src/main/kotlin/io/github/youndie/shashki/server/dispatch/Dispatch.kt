package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Who could take this ride, nearest first.
 *
 * One implementation in production — [GeoCandidateSource] over the geo-index — and a fixed list in
 * the tests that are about the saga rather than about matching. The port is what let B-11 and B-12
 * be built and tested before there was an index to build them on.
 */
public interface CandidateSource {
    public fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate>
}

public data class DriverCandidate(
    val driverId: String,
    val distanceMetres: Int,
    val rating: Double,
    /**
     * Where the driver is.
     *
     * **[distanceMetres] is a straight line and this is what a road can be found along.** The index
     * sorts by the first because it has to be cheap enough to run per offer; the wait a rider is
     * shown is the second, routed (B-31). Carrying the point here rather than asking the index again
     * keeps the two answers from being about different moments.
     */
    val at: GeoPoint,
)

/**
 * Which driver is spoken for by which ride. The EXECUTION step reserves, its compensation releases,
 * and "no reserved driver left behind" — B-11's acceptance — is a question to this object.
 */
public interface DriverReservations {
    /** False if the driver is already reserved for another ride. */
    public fun reserve(
        driverId: String,
        rideId: String,
    ): Boolean

    public fun release(
        driverId: String,
        rideId: String,
    )

    /**
     * Whether this driver is carrying somebody — or has just been offered to somebody.
     *
     * **Asked by the wait a rider is shown, not only by the dispatch** (B-42). "Available" was
     * computed in two places out of two different facts: the index answered "near" and the saga
     * answered "near and free", so a reserved driver produced a `0 min` tile for a car that could
     * never be matched.
     */
    public fun isReserved(driverId: String): Boolean

    /**
     * Free whoever is reserved for this ride, at the end of it.
     *
     * **By ride and not by driver, because the caller knows the ride.** The two ends of a ride —
     * `COMPLETED` and a cancellation after assignment — each have the ride's id in hand and would
     * otherwise have to look the driver up to release him, which is one more place to get wrong.
     */
    public fun releaseFor(rideId: String)

    public fun reservedFor(rideId: String): String?

    public fun all(): Map<String, String>
}

public class InMemoryDriverReservations : DriverReservations {
    private val byDriver = ConcurrentHashMap<String, String>()

    override fun reserve(
        driverId: String,
        rideId: String,
    ): Boolean = byDriver.putIfAbsent(driverId, rideId).let { it == null || it == rideId }

    override fun release(
        driverId: String,
        rideId: String,
    ) {
        byDriver.remove(driverId, rideId)
    }

    override fun isReserved(driverId: String): Boolean = byDriver.containsKey(driverId)

    override fun releaseFor(rideId: String) {
        byDriver.entries.removeIf { it.value == rideId }
    }

    override fun reservedFor(rideId: String): String? = byDriver.entries.firstOrNull { it.value == rideId }?.key

    override fun all(): Map<String, String> = byDriver.toMap()
}
