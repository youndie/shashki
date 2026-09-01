package io.github.youndie.shashki.server.feature.ride.saga

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * One driver's fifteen seconds, and what happens when they pass: the saga is resumed with
 * `IGNORED`, and the cascade moves to the next candidate.
 *
 * **In-process, and the gap is named.** These timers die with the process. What survives is the
 * saga's own TTL — the ninety-second matching budget the sweeper enforces — so after a restart an
 * offer nobody answers is not cascaded but rolled back within the budget: the hold released, the
 * driver freed, the rider told there are no cars. That is the correct degraded behaviour and it is
 * the sweeper's, not a timer's; a durable per-offer timer (petich-scheduler) buys back the cascade
 * across restarts and nothing else, which is why it is not here yet.
 */
public class OfferTimeouts(
    private val scope: CoroutineScope,
    private val onExpired: suspend (rideId: String, driverId: String) -> Unit,
) {
    private val timers = ConcurrentHashMap<String, Job>()

    public fun schedule(
        rideId: String,
        driverId: String,
        seconds: Long,
    ) {
        cancel(rideId)
        timers[rideId] =
            scope.launch {
                delay(seconds.seconds)
                timers.remove(rideId)
                onExpired(rideId, driverId)
            }
    }

    public fun cancel(rideId: String) {
        timers.remove(rideId)?.cancel()
    }

    /** For tests: fire a timer now rather than waiting fifteen seconds for it. */
    public fun pending(rideId: String): Boolean = timers.containsKey(rideId)
}
