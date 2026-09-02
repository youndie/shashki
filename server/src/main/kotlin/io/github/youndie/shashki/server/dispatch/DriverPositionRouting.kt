package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DRIVER_TICKET_QUERY
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.workinprogress.petich.PetichClock
import java.util.concurrent.atomic.AtomicLong

/**
 * The position stream: one socket per driver, a [DriverReport] every few seconds, straight into the
 * index.
 *
 * **Nothing here reaches the broker, and that is research §1.6a rather than an omission.** A
 * position is worth a minute; a topic partitioned by ride would carry thousands of them a second to
 * be read once and thrown away. What goes through booblik is what happened to a *ride*.
 *
 * **Auth tier: a driver ticket, when a provider is configured** (B-52). A browser cannot put a
 * header on a WebSocket, so the upgrade carries a one-shot ticket minted behind the ordinary token
 * check — `DriverTickets` says why that beat sending the token as the first frame. Without a ticket
 * the upgrade is refused before a frame is read.
 *
 * **A frame is compared, not relabelled.** This is the one place the token does not simply replace
 * the claimed id: rewriting it would file another driver's position under the connected one, which
 * is worse than losing it. A frame for anybody else is dropped and counted, and the count is what
 * makes a client's bug visible rather than mysterious.
 *
 * The class and the rating are still self-reported — the seam `DriverReport` names, and a different
 * item: they belong to a driver record this product does not have.
 *
 * A closed socket is a driver going offline. A crashed one is covered by staleness instead, which
 * is why the index does not need to hear about it.
 */
public fun Route.driverPositionRoutes(protected: Boolean = false) {
    val index by inject<DriverIndex>()
    val clock by inject<PetichClock>()
    val json by inject<Json>()
    val tickets by inject<DriverTickets>()
    val dropped by inject<DroppedFrames>()
    val drivers by inject<DriverRepository>()
    val log = LoggerFactory.getLogger("shashki.positions")

    webSocket(DRIVER_POSITIONS_PATH) {
        // Refused before a frame is read, and closed rather than answered: there is no status code
        // to send once an upgrade has completed, so the policy-violation close is the 401.
        val subject =
            if (protected) {
                call.request.queryParameters[DRIVER_TICKET_QUERY]?.let(tickets::redeem)
                    ?: return@webSocket close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "a driver ticket is required"),
                    )
            } else {
                null
            }

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
                if (subject != null && report.driverId != subject) {
                    dropped.record()
                    log.warn("a socket reported a position for a driver it is not signed in as")
                    continue
                }
                // **The class is the record's, not the frame's** (B-63). A driver telling the
                // server which class they drive is a driver choosing which offers they are eligible
                // for; the record ends that with no new check. A driver this server has never heard
                // of is not indexed at all — a visible failure rather than a silent promotion.
                val known = drivers.find(report.driverId)
                if (known == null) {
                    log.warn("a position for a driver with no record: {}", report.driverId)
                    continue
                }
                driverId = report.driverId
                index.report(report.copy(rideClass = known.rideClass), clock.nowEpochMs())
                // **The acknowledgement, and it is the whole of B-54.** The driver's screen counts
                // positions "the socket actually took"; before this it counted the frames the client
                // had *written*, so a shift whose every frame was refused read `19 positions sent`
                // while the server discarded all nineteen — the exact failure the count exists to
                // make visible, hidden by the count. Nothing is sent for a frame that was dropped
                // above, which is what makes the number mean something.
                //
                // The report itself rather than a bare token: the contract is already
                // `Flow<DriverReport>` on both sides, and one frame every four seconds is not a
                // bandwidth question.
                send(Frame.Text(json.encodeToString(DriverReport.serializer(), report)))
            }
        } finally {
            driverId?.let(index::goOffline)
        }
    }
}

/**
 * How many position frames were for somebody other than the driver who opened the socket (B-52).
 *
 * In memory and per process, like the degradation counter: what it is for is a graph and an alert.
 * **It exists because "dropped" and "never sent" are indistinguishable from the outside** — a client
 * whose id and token disagree would otherwise simply not appear on the map, and nobody could tell
 * that from a phone with no signal.
 */
public class DroppedFrames {
    private val count = AtomicLong()

    public fun record(): Unit = count.incrementAndGet().let { }

    public fun total(): Long = count.get()
}
