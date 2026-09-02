package io.github.youndie.shashki.server.feature.events.domain

import io.github.youndie.shashki.protocol.RideEventView
import io.github.youndie.shashki.protocol.RideHistoryView
import java.util.concurrent.ConcurrentHashMap

/**
 * What happened to a ride, according to the topic.
 *
 * **A read model, and the point is where it comes from.** Everything else this server answers is
 * read off the saga's row; this is built only from records a consumer took off the broker. A
 * publisher with nobody reading it is indistinguishable from the log line it replaced, so the
 * consumer is what makes the seam observable from outside (B-38).
 *
 * **In memory, and rebuilt from the log on start.** That is not a shortcut — it is what a log-backed
 * projection *is*: the broker holds the record and the reader holds a position, so a restarted
 * consumer replays from the earliest offset the log still has and arrives at the same answer. The
 * limit is honest and named: what retention has dropped is not in here, and no database would bring
 * it back either.
 */
public interface RideHistory {
    public fun of(rideId: String): RideHistoryView

    /** One record from the topic. [offset] is the broker's, and is what makes the order checkable. */
    public fun record(
        rideId: String,
        type: String,
        offset: Long,
    )
}

public class InMemoryRideHistory : RideHistory {
    private val byRide = ConcurrentHashMap<String, MutableList<RideEventView>>()

    override fun of(rideId: String): RideHistoryView = RideHistoryView(rideId, byRide[rideId]?.toList().orEmpty())

    override fun record(
        rideId: String,
        type: String,
        offset: Long,
    ) {
        val events = byRide.computeIfAbsent(rideId) { mutableListOf() }
        synchronized(events) {
            // **Idempotent by offset**, because a consumer that reconnects re-reads from where it
            // was and a batch can arrive twice. A projection that counted a duplicate would say a
            // ride was settled twice, which is exactly the kind of thing a reader would believe.
            if (events.none { it.offset == offset }) {
                events += RideEventView(type, offset)
                events.sortBy { it.offset }
            }
        }
    }
}
