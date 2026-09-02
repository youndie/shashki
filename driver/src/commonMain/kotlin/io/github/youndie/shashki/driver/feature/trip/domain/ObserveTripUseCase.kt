package io.github.youndie.shashki.driver.feature.trip.domain

import io.github.youndie.shashki.protocol.RideView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The accepted ride, polled.
 *
 * **It ends when the rider cancels and not otherwise**, because nothing else in this bundle can move
 * the ride: `ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED` are the driver's transitions and the
 * server has no route for them yet. That is stated rather than stubbed — a button that posted to an
 * endpoint that does not exist would be worse than its absence. See B-29's own note.
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
