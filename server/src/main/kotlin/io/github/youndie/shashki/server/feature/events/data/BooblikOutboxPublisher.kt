package io.github.youndie.shashki.server.feature.events.data

import io.github.youndie.shashki.server.feature.events.EventsConfig
import kotlinx.coroutines.CoroutineScope
import ru.workinprogress.booblik.TopicName
import ru.workinprogress.booblik.net.client.BooblikConnection
import ru.workinprogress.booblik.net.client.Producer
import ru.workinprogress.booblik.net.client.TopicHandle
import ru.workinprogress.petich.outbox.OutboxPublisher
import ru.workinprogress.petich.outbox.OutboxRecord
import java.net.InetSocketAddress

/**
 * The far end of the outbox: petich's records, on booblik's log.
 *
 * **Throwing is the correct behaviour and is why this is four lines rather than forty.**
 * `OutboxRelayWorker` retries with exponential backoff and dead-letters after five attempts, so a
 * publisher that swallowed a broker outage would be taking a decision the relay already makes
 * better. What must not happen is what happened before B-38 — an event marked delivered because
 * something wrote a log line.
 *
 * **The key is the ride's id**, which is what makes the topic useful: booblik picks the partition
 * from the key client-side, so everything that happens to one ride lands on one partition in the
 * order it happened. A consumer reading a ride's history reads a sequence rather than a set.
 *
 * The type goes on the wire as part of the payload rather than beside it — booblik carries bytes and
 * nothing else, which is the same austerity that removed its group coordinator.
 */
public class BooblikOutboxPublisher(
    address: InetSocketAddress,
    scope: CoroutineScope,
) : OutboxPublisher,
    AutoCloseable {
    private val connection = BooblikConnection(address, scope)
    private val producer = Producer(connection, scope)
    private var handle: TopicHandle? = null

    override suspend fun publish(event: OutboxRecord) {
        val topic = handle ?: producer.topic(TopicName(EventsConfig.TOPIC)).also { handle = it }
        // Awaited: `send` queues, and a relay that marked an event delivered before the broker
        // acknowledged it would have an outbox with the same hole the log publisher had.
        topic.send(envelope(event), key = rideIdOf(event).toByteArray()).await()
    }

    override fun close() {
        producer.close()
        connection.close()
    }

    private fun envelope(event: OutboxRecord): ByteArray =
        """{"id":"${event.id}","type":"${event.type}","payload":${event.payload}}""".toByteArray()

    /**
     * The ride an event is about, taken from its id.
     *
     * The saga writes `"<rideId>:assigned"` and `"<rideId>:settled"`, so the id is the ride's with a
     * suffix — a fact this reads rather than a second field to keep in step. If a future event ever
     * has a different shape of id, the partition is wrong and nothing else is, which is the failure
     * this can afford.
     */
    private fun rideIdOf(event: OutboxRecord): String = event.id.substringBeforeLast(':')
}
