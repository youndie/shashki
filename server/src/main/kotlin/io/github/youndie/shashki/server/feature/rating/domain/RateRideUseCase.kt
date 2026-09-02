package io.github.youndie.shashki.server.feature.rating.domain

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository

/**
 * The rider rates the ride (B-44).
 *
 * **Only a finished one, and only once.** A rating before `COMPLETED` would be a rating of something
 * that has not happened — the kit's R8 is the screen after the trip — and a second one for the same
 * ride collides on the primary key rather than quietly replacing the first. Both refusals are the
 * repository's shape rather than a rule written here twice.
 */
public class RateRideUseCase(
    private val rides: RideRepository,
    private val ratings: RatingRepository,
) : UseCase<RateRideUseCase.Params, Rating> {
    override suspend fun invoke(params: Params): Result<Rating> =
        suspendRunCatching {
            require(params.stars in STARS) { "a rating of ${params.stars} is not one to five" }
            val ride = rides.find(params.rideId) ?: throw RideNotFoundException(params.rideId)
            if (ride.status != RideStatus.COMPLETED) throw NotFinishedException(params.rideId, ride.status)
            val driverId = ride.driverId ?: throw NotFinishedException(params.rideId, ride.status)

            val rating = Rating(params.rideId, driverId, params.stars)
            ratings.record(rating)
            rating
        }

    public class Params(
        public val rideId: String,
        public val stars: Int,
    )

    private companion object {
        val STARS = 1..5
    }
}

/** Asked to end something that has not ended. A 409, like the settlement's own refusal. */
public class NotFinishedException(
    public val rideId: String,
    public val status: RideStatus,
) : RuntimeException("ride $rideId is $status, not COMPLETED")
