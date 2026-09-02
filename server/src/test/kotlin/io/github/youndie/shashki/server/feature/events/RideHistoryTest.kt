package io.github.youndie.shashki.server.feature.events

import io.github.youndie.shashki.server.feature.events.domain.InMemoryRideHistory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The projection, which is the only part of the consumer that has a rule in it.
 *
 * Everything else `BooblikRideHistory` does is read a flow and decode a record; what it must not do
 * is count the same event twice, because a reconnecting consumer re-reads from where it was.
 */
class RideHistoryTest {
    private val history = InMemoryRideHistory()

    @Test
    fun `a ride's events come back in the broker's order`() {
        history.record("ride-1", "ride.settled", offset = 7)
        history.record("ride-1", "ride.assigned", offset = 3)

        assertEquals(
            listOf("ride.assigned" to 3L, "ride.settled" to 7L),
            history.of("ride-1").events.map { it.type to it.offset },
        )
    }

    /**
     * **A batch can arrive twice and the projection must not believe it.** A consumer that
     * reconnected and re-read would otherwise say a ride was settled twice — which is exactly the
     * kind of statement a reader takes at face value.
     */
    @Test
    fun `the same offset recorded twice is one event`() {
        repeat(3) { history.record("ride-1", "ride.assigned", offset = 3) }

        assertEquals(1, history.of("ride-1").events.size)
    }

    /** A ride the topic has nothing about is an empty history, not a missing one. */
    @Test
    fun `a ride the broker has never mentioned has an empty history`() {
        history.record("ride-1", "ride.assigned", offset = 1)

        val other = history.of("ride-2")

        assertEquals("ride-2", other.rideId)
        assertEquals(emptyList(), other.events)
    }
}
