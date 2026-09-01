package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import ru.workinprogress.petich.PetichClock

/**
 * The candidate query: the online drivers of the requested class, nearest first, better rated
 * first among equals.
 *
 * The saga asks this and nothing else — it does not know there is a grid behind it, which is what
 * lets B-11 and B-12 be tested against a list and demoed against the index.
 */
public class GeoCandidateSource(
    private val index: DriverIndex,
    private val clock: PetichClock,
) : CandidateSource {
    override fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate> = index.near(pickup, rideClass, clock.nowEpochMs())
}
