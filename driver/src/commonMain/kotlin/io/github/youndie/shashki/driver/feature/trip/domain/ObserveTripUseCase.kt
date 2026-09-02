package io.github.youndie.shashki.driver.feature.trip.domain

import io.github.youndie.shashki.driver.UseCase
import io.github.youndie.shashki.driver.suspendRunCatching
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The accepted ride, polled.
 *
 * It ends when the ride does — completed by this driver, or cancelled by the rider. B-29 shipped
 * this screen without buttons because the server had no route for the transitions; B-37 built them,
 * and the poll is now one of two things that move the state rather than the only one.
 */
public class ObserveTripUseCase(
    private val trips: TripRepository,
    private val interval: Duration = 3.seconds,
) {
    public operator fun invoke(rideId: String): Flow<Result<RideView>> =
        flow {
            while (true) {
                emit(runCatching { trips.read(rideId) })
                delay(interval)
            }
        }
}

/**
 * The driver says the trip has moved.
 *
 * **The next state is asked of the server, not decided here.** `TripProgression` is the server's and
 * so is the refusal — a client with its own copy of the order would be a second opinion about when
 * somebody gets charged. What this bundle knows is which button to draw, which is a different
 * question with the same answer most of the time and a worse failure when it is not.
 */
public class AdvanceTripUseCase(
    private val trips: TripRepository,
) : UseCase<AdvanceTripUseCase.Params, RideView> {
    override suspend fun invoke(params: Params): Result<RideView> =
        suspendRunCatching { trips.advance(params.rideId, params.driverId, params.to) }

    public class Params(
        public val rideId: String,
        public val driverId: String,
        public val to: RideStatus,
    )
}
