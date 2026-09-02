package io.github.youndie.shashki.rider.feature.ride.domain

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.UseCase
import io.github.youndie.shashki.rider.suspendRunCatching
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the journey costs in each class, for R4's three tiles. */
public class QuoteJourneyUseCase(
    private val rides: RideRepository,
) : UseCase<QuoteJourneyUseCase.Params, QuotesView> {
    override suspend fun invoke(params: Params): Result<QuotesView> =
        suspendRunCatching { rides.quotes(params.from, params.to) }

    public class Params(
        public val from: GeoPoint,
        public val to: GeoPoint,
    )
}

/** Ask for a car. The saga parks at `MATCHING` and the answer comes back through [ObserveRideUseCase]. */
public class RequestRideUseCase(
    private val rides: RideRepository,
) : UseCase<RequestRideUseCase.Params, RideView> {
    override suspend fun invoke(params: Params): Result<RideView> =
        suspendRunCatching {
            rides.request(
                RideRequest(
                    riderId = params.riderId,
                    pickup = params.from,
                    dropoff = params.to,
                    rideClass = params.rideClass,
                    paymentMethodId = params.paymentMethodId,
                ),
            )
        }

    public class Params(
        public val riderId: String,
        public val from: GeoPoint,
        public val to: GeoPoint,
        public val rideClass: RideClass,
        public val paymentMethodId: String,
    )
}

/**
 * The ride, until it stops changing.
 *
 * **Polling, and it says so in its own name and shape.** The server offers no subscription for a
 * rider — the driver's socket is the driver's — so this is a loop, and putting it behind a `Flow`
 * that looked like a subscription would hide the interval from whoever tunes it. It stops on its own
 * at a terminal status so a finished ride does not poll for ever in a background tab.
 */
public class ObserveRideUseCase(
    private val rides: RideRepository,
    private val interval: Duration = DEFAULT_INTERVAL,
) {
    public operator fun invoke(rideId: String): Flow<Result<RideView>> =
        flow {
            while (true) {
                val result = suspendRunCatching { rides.read(rideId) }
                emit(result)
                if (result.getOrNull()?.status in TERMINAL) return@flow
                delay(interval)
            }
        }

    public companion object {
        public val DEFAULT_INTERVAL: Duration = 2.seconds

        /**
         * Where a ride stops moving. `RideStatus` has exactly two such states — a rejected order
         * never becomes a ride at all, it fails the request — so this is the whole list rather than
         * a selection from it.
         */
        public val TERMINAL: Set<RideStatus> = setOf(RideStatus.COMPLETED, RideStatus.CANCELLED)
    }
}

/** Calling it off. Kept a use case rather than a repository call because the saga compensates. */
public class CancelRideUseCase(
    private val rides: RideRepository,
) : UseCase<String, Unit> {
    override suspend fun invoke(params: String): Result<Unit> = suspendRunCatching { rides.cancel(params) }
}

/**
 * Where the car is, over and over.
 *
 * **Its own loop, faster than the ride's**, because the two facts move at different speeds: a status
 * changes a handful of times in twenty minutes and a car moves continuously. It never terminates on
 * its own — the screen's lifetime is the loop's lifetime — and a failed poll is skipped rather than
 * emitted, since a car whose phone is quiet for one interval has not gone anywhere.
 */
public class WatchDriverUseCase(
    private val rides: RideRepository,
    private val interval: Duration = DEFAULT_INTERVAL,
) {
    public operator fun invoke(rideId: String): Flow<AssignedDriverView> =
        flow {
            while (true) {
                suspendRunCatching { rides.driver(rideId) }.getOrNull()?.let { emit(it) }
                delay(interval)
            }
        }

    /** The road the rider watches the car travel along. Asked once; the road does not change. */
    public suspend fun roadFor(ride: RideView): List<GeoPoint> = rides.route(ride.pickup, ride.dropoff).geometry

    public companion object {
        public val DEFAULT_INTERVAL: Duration = 3.seconds
    }
}
