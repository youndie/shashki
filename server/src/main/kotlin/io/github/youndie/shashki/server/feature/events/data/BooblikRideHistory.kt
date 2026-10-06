package io.github.youndie.shashki.server.feature.events.data

import io.github.youndie.booblik.Offset
import io.github.youndie.booblik.PartitionId
import io.github.youndie.booblik.TopicName
import io.github.youndie.booblik.net.client.BooblikSubscriber
import io.github.youndie.booblik.net.client.FetchFailedException
import io.github.youndie.booblik.net.client.StartPosition
import io.github.youndie.booblik.net.wire.ErrorCode
import io.github.youndie.shashki.server.feature.events.EventsConfig
import io.github.youndie.shashki.server.feature.events.domain.RideHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The consumer: `ride-events`, followed, into a projection.
 *
 * **A separate concern from the saga that wrote them**, which is the requirement rather than a
 * nicety. It shares a process with the producer today because this product is one deployment on
 * purpose (see `server/build.gradle.kts`), and it shares nothing else: it holds no transaction, it
 * reads the broker rather than the database, and taking it out into its own process would be a move
 * rather than a rewrite.
 *
 * **From `Earliest`, which is the start of the live log rather than zero.** Retention moves it, so a
 * restarted server rebuilds what the broker still holds and no more — the honest limit of a
 * projection with no store of its own, stated where somebody would otherwise assume a database.
 *
 * **A broker that goes away is not the end of the reader (B-103).** booblik's subscription has no
 * retry of its own — a closed connection ends the flow with an exception — and this used to collect it
 * once, so the first broker restart stopped the history for good, silently. Now each partition has a
 * reader of its own that says what happened, waits [retryAfter] and follows again from the NEXT offset
 * of that partition: a restart costs a reconnect, not a replay of the log. Per partition because a
 * subscription takes one start position for all of them, and each has got to a different place.
 *
 * A record it cannot read is dropped with a line rather than stopping the stream: one malformed
 * event is one event, and a consumer that died on it would stop reading everything after it.
 */
public class BooblikRideHistory(
    private val address: InetSocketAddress,
    private val history: RideHistory,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val retryAfter: Duration = 1.seconds,
) : AutoCloseable {
    private val subscriber = BooblikSubscriber(address)
    private val topic = TopicName(EventsConfig.TOPIC)
    private var job: Job? = null

    public fun start(scope: CoroutineScope): Job =
        scope
            .launch {
                partitions().forEach { partition -> launch { follow(partition) } }
            }.also { job = it }

    // The partitions, asked of the broker — and asked again until it answers: a server that starts
    // before its broker must still end up reading.
    private suspend fun partitions(): List<PartitionId> {
        while (true) {
            try {
                return subscriber.partitionsOf(topic)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                LOG.warn(
                    "the broker at {} does not answer yet; asking again in {}: {}",
                    address,
                    retryAfter,
                    failure.message,
                )
                delay(retryAfter)
            }
        }
    }

    private suspend fun CoroutineScope.follow(partition: PartitionId) {
        // Where this partition's reader has got to; null until the first batch, which is `Earliest`.
        var next: Offset? = null
        while (isActive) {
            try {
                subscriber
                    .follow(
                        topic,
                        from = next?.let { StartPosition.At(it) } ?: StartPosition.Earliest,
                        partitions = listOf(partition),
                    ).collect { batch ->
                        batch.records.forEachIndexed { index, bytes -> read(bytes, batch.baseOffset.value + index) }
                        next = batch.nextOffset
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (failure is FetchFailedException && failure.code == ErrorCode.OFFSET_OUT_OF_RANGE) {
                    // Retention passed this reader while it was away: what it had not read is gone, which
                    // is the projection's stated limit. Said out loud, then on from the live log's start.
                    LOG.warn(
                        "partition {}: offset {} is no longer in the log; carrying on from its start",
                        partition.value,
                        next?.value,
                    )
                    next = null
                } else {
                    LOG.warn(
                        "partition {}: the broker went away at offset {}; following again in {}: {}",
                        partition.value,
                        next?.value,
                        retryAfter,
                        failure.message,
                    )
                }
                delay(retryAfter)
            }
        }
    }

    private fun read(
        bytes: ByteArray,
        offset: Long,
    ) {
        runCatching {
            val envelope = json.parseToJsonElement(bytes.decodeToString()).jsonObject
            val id = envelope.getValue("id").jsonPrimitive.content
            val type = envelope.getValue("type").jsonPrimitive.content
            history.record(rideId = id.substringBeforeLast(':'), type = type, offset = offset)
        }.onFailure { LOG.warn("dropping an unreadable record at offset {}: {}", offset, it.message) }
    }

    override fun close() {
        job?.cancel()
        subscriber.close()
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(BooblikRideHistory::class.java)
    }
}
