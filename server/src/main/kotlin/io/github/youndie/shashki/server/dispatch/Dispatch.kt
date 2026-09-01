package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import java.util.concurrent.ConcurrentHashMap

/** Who could take this ride, nearest first. The geo-index and the simulator (B-20) are the real one. */
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
)

/**
 * A fixed list, so the saga has someone to reserve. Research §1.4d: "the saga itself can be built
 * and killed at phase boundaries against a stub candidate list." Three drivers because B-12's
 * cascade needs more than one to cascade over, and an empty list because "no cars nearby" is a
 * state the kit draws.
 */
public class FixedCandidateSource(
    private val candidates: List<DriverCandidate> = DEFAULT,
) : CandidateSource {
    override fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate> = candidates

    public companion object {
        public val DEFAULT: List<DriverCandidate> =
            listOf(
                DriverCandidate("driver-1", distanceMetres = 800, rating = 4.9),
                DriverCandidate("driver-2", distanceMetres = 1_400, rating = 4.7),
                DriverCandidate("driver-3", distanceMetres = 2_100, rating = 4.8),
            )
    }
}

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

    override fun reservedFor(rideId: String): String? = byDriver.entries.firstOrNull { it.value == rideId }?.key

    override fun all(): Map<String, String> = byDriver.toMap()
}
