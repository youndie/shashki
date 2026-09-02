package io.github.youndie.shashki.server.feature.events.data

import io.github.youndie.shashki.server.feature.events.EventsConfig
import io.github.youndie.shashki.server.feature.events.domain.RideHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.workinprogress.booblik.TopicName
import ru.workinprogress.booblik.net.client.BooblikSubscriber
import ru.workinprogress.booblik.net.client.StartPosition
import java.net.InetSocketAddress

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
 * A record it cannot read is dropped with a line rather than stopping the stream: one malformed
 * event is one event, and a consumer that died on it would stop reading everything after it.
 */
public class BooblikRideHistory(
    private val address: InetSocketAddress,
    private val history: RideHistory,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AutoCloseable {
    private val subscriber = BooblikSubscriber(address)
    private var job: Job? = null

    public fun start(scope: CoroutineScope): Job =
        scope
            .launch {
                subscriber.follow(TopicName(EventsConfig.TOPIC), from = StartPosition.Earliest).collect { batch ->
                    batch.records.forEachIndexed { index, bytes ->
                        read(bytes, batch.baseOffset.value + index)
                    }
                }
            }.also { job = it }

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
