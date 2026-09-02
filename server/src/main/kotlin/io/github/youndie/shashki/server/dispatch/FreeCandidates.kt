package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass

/**
 * The candidates who are near **and** not already carrying somebody.
 *
 * **One list, so that "available" means one thing** (B-42). The index answers a question about
 * geography and knows nothing about reservations; the order saga asked it and then filtered by
 * reserving, and `PickupEta` asked it and did not. The two answers disagreed the moment a driver was
 * on a ride: the rider's tile went on showing a wait for a car the dispatch would refuse — and,
 * while the reservation leaked, went on showing it for ever.
 *
 * Wrapping rather than teaching [GeoCandidateSource] about reservations, because the index is a
 * cache of positions and that is all it should be: the day the index is shared between processes,
 * this is the layer that stays local.
 *
 * **The saga still reserves.** A filter is a read and two orders can pass it at once; the atomic
 * step is `reserve`, which returns false for the loser. This removes the drivers who are known to be
 * taken, not the race.
 */
public class FreeCandidates(
    private val source: CandidateSource,
    private val reservations: DriverReservations,
) : CandidateSource {
    override fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate> = source.candidates(pickup, rideClass).filterNot { reservations.isReserved(it.driverId) }
}
