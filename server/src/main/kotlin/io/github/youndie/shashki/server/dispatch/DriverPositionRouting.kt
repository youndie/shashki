package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverReport
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.workinprogress.petich.PetichClock

/**
 * The position stream: one socket per driver, a [DriverReport] every few seconds, straight into the
 * index.
 *
 * **Nothing here reaches the broker, and that is research §1.6a rather than an omission.** A
 * position is worth a minute; a topic partitioned by ride would carry thousands of them a second to
 * be read once and thrown away. What goes through booblik is what happened to a *ride*.
 *
 * **Auth tier: public, temporarily, and the hole is named.** The socket believes the `driverId`,
 * the class and the rating it is told. Until B-09 puts the driver in a token, anyone who can reach
 * the port can park a five-star driver next to a pickup. The seam is the report itself — see
 * `DriverReport`'s own note — and the tier moves to "driver token, id taken from it, class and
 * rating read from the driver's row" in the same change.
 *
 * A closed socket is a driver going offline. A crashed one is covered by staleness instead, which
 * is why the index does not need to hear about it.
 */
public fun Route.driverPositionRoutes() {
    val index by inject<DriverIndex>()
    val clock by inject<PetichClock>()
    val json by inject<Json>()
    val log = LoggerFactory.getLogger("shashki.positions")

    webSocket(DRIVER_POSITIONS_PATH) {
        var driverId: String? = null
        try {
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val report =
                    runCatching { json.decodeFromString(DriverReport.serializer(), text) }
                        .getOrElse {
                            // A malformed frame is one driver's bug, not the stream's end: log it
                            // and keep reading, or one bad client takes its own socket down and
                            // silently stops being a candidate.
                            log.warn("dropping unreadable position frame: {}", it.message)
                            continue
                        }
                driverId = report.driverId
                index.report(report, clock.nowEpochMs())
            }
        } finally {
            driverId?.let(index::goOffline)
        }
    }
}
