package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.feature.rating.domain.RatingRepository

/**
 * Candidates whose rating is the one riders gave them (B-44).
 *
 * **Until this, the rating on a candidate was the driver's own claim**: it arrives in every position
 * frame, `DriverReport` says so in its own note, and the sort's second key was therefore a number the
 * sorted party chose. Now it is the average of what riders recorded, and the frame's value is the
 * fallback for a driver nobody has rated yet.
 *
 * **The sort itself did not change, and that is the item's other branch.** Distance first, rating as
 * the tiebreaker — see research §1.6d for what a distance-to-rating trade would cost and why no
 * coefficient was invented for it.
 */
public class RatedCandidates(
    private val source: CandidateSource,
    private val ratings: RatingRepository,
) : CandidateSource {
    override fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate> =
        source.candidates(pickup, rideClass).map { candidate ->
            ratings.averageFor(candidate.driverId)?.let { candidate.copy(rating = it) } ?: candidate
        }
}
