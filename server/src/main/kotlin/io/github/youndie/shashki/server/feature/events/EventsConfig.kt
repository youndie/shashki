package io.github.youndie.shashki.server.feature.events

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Where the broker is, or the fact that there is none.
 *
 * **`null` is a running configuration, and what it costs is written down rather than papered over.**
 * Without a broker the relay is **not started**: the events stay in the outbox, unpublished and
 * undelivered, which is true. The alternative was what this server did until B-38 — a
 * `LoggingPublisher` that marked every event delivered because it had written a line — and that is
 * not a fallback, it is a broker outage nobody can notice.
 */
public object EventsConfig {
    public const val ADDRESS_VARIABLE: String = "SHASHKI_BOOBLIK"

    /** The one topic. booblik fixes its partitions at startup, so this name is also a deployment fact. */
    public const val TOPIC: String = "ride-events"

    private val LOG = LoggerFactory.getLogger(EventsConfig::class.java)

    public fun address(env: (String) -> String? = System::getenv): InetSocketAddress? {
        val raw = env(ADDRESS_VARIABLE)?.takeIf { it.isNotBlank() } ?: return warnAndStop()
        val host = raw.substringBeforeLast(':', raw)
        val port = raw.substringAfterLast(':', "").toIntOrNull() ?: DEFAULT_PORT
        return InetSocketAddress(host, port)
    }

    private fun warnAndStop(): InetSocketAddress? {
        LOG.warn(
            "no {}: the outbox relay is not started, so a ride's events are written and never " +
                "delivered. They are not lost — they are rows nobody reads",
            ADDRESS_VARIABLE,
        )
        return null
    }

    private const val DEFAULT_PORT = 9092
}

/**
 * The broker's two halves, or the fact that there is neither.
 *
 * **A wrapper because Koin resolves by type and a type cannot be nullable**: `get<T>` is bound to
 * `T : Any`, so a `single<BooblikOutboxPublisher?>` does not compile. The client's `CrashReporting`
 * is the same shape for a different reason — there, a `single` returning null would have failed at
 * injection with a message about the type rather than about the configuration. Either way the
 * absence is a value the reader can see.
 */
public class Events(
    public val publisher: io.github.youndie.shashki.server.feature.events.data.BooblikOutboxPublisher?,
    public val consumer: io.github.youndie.shashki.server.feature.events.data.BooblikRideHistory?,
)
