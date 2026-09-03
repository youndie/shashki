package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DRIVER_TICKET_QUERY
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.protocol.Routes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What a simulated driver does when offered a ride. B-12's acceptance needs the last two. */
public enum class SimulatedBehaviour { ACCEPT, DECLINE, IGNORE }

public data class SimulatorConfig(
    val drivers: Int = 20,
    val centre: GeoPoint = LJUBLJANA,
    val radiusMetres: Double = 3_000.0,
    val reportInterval: Duration = 3.seconds,
    val pollInterval: Duration = 300.milliseconds,
    val speedMetresPerSecond: Double = 8.0,
    val behaviour: (driverId: String) -> SimulatedBehaviour = { SimulatedBehaviour.ACCEPT },
    /**
     * Which class each driver drives. Spread across the three by default, because a city has all
     * three; pinned to one by a test that needs several candidates for the *same* request — a
     * cascade over drivers of three different classes is a cascade of length one.
     */
    val rideClass: (driverId: String) -> RideClass? = { null },
    val seed: Int = 20_260_902,
    /**
     * How a simulated driver signs in (B-52).
     *
     * **A simulator with a back door is the back door**, which is the item's own line: these are
     * clients of the same routes, and those routes now want a token. The lambda is given the
     * driver's id and answers with a bearer token for it, or `null` on a server with no provider —
     * the configuration every test here uses. What it must never be is a way into the index that a
     * real client does not have.
     */
    val token: suspend (driverId: String) -> String? = { null },
    /**
     * A one-shot ticket for the position socket, for the same reason the driver's application has
     * one: a WebSocket upgrade cannot carry a header.
     */
    val ticket: suspend (driverId: String) -> String? = { null },
) {
    public companion object {
        public val LJUBLJANA: GeoPoint = GeoPoint(46.0511, 14.5051)
    }
}

/**
 * Virtual drivers, so the demo has cars without having people.
 *
 * **A client of the same socket and the same HTTP API a real driver's app would use**, which is the
 * item's own condition: there is no back door into the index. Everything it does — report a
 * position, read the offer, answer it — is a request the driver's application makes, so anything
 * that works here works for a real client and anything broken here is broken for one.
 *
 * **Straight lines, until B-23.** Each driver walks towards a waypoint and picks a new one on
 * arrival. The road graph is what makes this look real and it is a different item; the property
 * that matters for matching — drivers are in different places and the places change — does not
 * depend on the road bending.
 */
public class DriverSimulator(
    private val client: HttpClient,
    private val config: SimulatorConfig = SimulatorConfig(),
    private val json: Json = Json,
) {
    private val log = LoggerFactory.getLogger("shashki.simulator")

    /**
     * Two loops per driver, because a real driver's app has two: one pushes position every few
     * seconds, one watches for an offer several times a second. Folding them into one made the
     * cascade take a reporting interval per decline — which is the simulator being slow, read as
     * the server being slow.
     */
    public fun start(scope: CoroutineScope): List<Job> =
        (1..config.drivers).flatMap { n ->
            val driverId = "sim-$n"
            val random = Random(config.seed + n)
            listOf(
                scope.launch { report(driverId, random) },
                scope.launch { watchForOffers(driverId) },
            )
        }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "closing a socket that is already gone is not a failure anybody can act on",
    )
    private suspend fun report(
        driverId: String,
        random: Random,
    ) {
        val rideClass = config.rideClass(driverId) ?: RideClass.entries[random.nextInt(RideClass.entries.size)]
        val rating = MIN_RATING + random.nextDouble() * (MAX_RATING - MIN_RATING)
        var at = randomPointNear(config.centre, config.radiusMetres, random)
        // The road ahead, as vertices still to be reached. Empty means "pick somewhere new and ask
        // for the way there" — see [roadTo].
        var ahead = ArrayDeque<GeoPoint>()

        val ticket = config.ticket(driverId)
        val path = if (ticket == null) DRIVER_POSITIONS_PATH else "$DRIVER_POSITIONS_PATH?$DRIVER_TICKET_QUERY=$ticket"
        // Hoisted: a request builder is not a suspend lambda, and the token is fetched rather than
        // held — this is where a simulated driver "signs in".
        val token = config.token(driverId)
        val session = client.webSocketSession(path) { token?.let(::bearer) }
        try {
            while (currentCoroutineContext().isActive) {
                session.send(
                    Frame.Text(
                        json.encodeToString(DriverReport.serializer(), DriverReport(driverId, rideClass, rating, at)),
                    ),
                )
                delay(config.reportInterval)
                if (ahead.isEmpty()) {
                    ahead = ArrayDeque(roadTo(at, randomPointNear(config.centre, config.radiusMetres, random)))
                }
                at =
                    advance(at, ahead, config.speedMetresPerSecond * config.reportInterval.inWholeMilliseconds / MILLIS)
            }
        } finally {
            runCatching { session.close() }
        }
    }

    /**
     * The way from [from] to [to], asked of the server the same way a driver's application would —
     * over `POST /api/routes` rather than by reaching into the estimator this process also holds.
     *
     * **A simulated car that cuts across blocks is a demo that shows the map is decorative.** The
     * whole point of the pair of cars on the rider's screen is that they are on streets. When the
     * server cannot route — no extract configured, or a point off the graph — the car falls back to
     * the straight line, because a simulator that stopped driving would break the parts of the demo
     * that have nothing to do with routing. That fallback is logged, and it is deliberately not what
     * any test asserts on.
     */
    private suspend fun roadTo(
        from: GeoPoint,
        to: GeoPoint,
    ): List<GeoPoint> =
        runCatching {
            val response =
                client.post(Routes()) {
                    contentType(ContentType.Application.Json)
                    setBody(RouteRequest(from = from, to = to))
                }
            response.body<RouteView>().geometry.takeIf { it.size >= 2 } ?: listOf(to)
        }.getOrElse {
            // `warn` and not `debug`: a simulated car that stops using the roads still moves, so
            // this failure is invisible in the demo and looks like a rendering problem in the map.
            log.warn("no road from {} to {}, driving straight: {}", from, to, it.message)
            listOf(to)
        }

    /**
     * Move [metres] along [ahead], consuming the vertices passed. The car stops on the road rather
     * than between it and wherever it was going, which is what makes the position it reports a place
     * a car could be.
     */
    private fun advance(
        from: GeoPoint,
        ahead: ArrayDeque<GeoPoint>,
        metres: Double,
    ): GeoPoint {
        var at = from
        var budget = metres
        while (ahead.isNotEmpty()) {
            val next = ahead.first()
            val distance = metresBetween(at, next)
            if (distance > budget) return moveTowards(at, next, budget)
            at = next
            budget -= distance
            ahead.removeFirst()
        }
        return at
    }

    private suspend fun watchForOffers(driverId: String) {
        while (currentCoroutineContext().isActive) {
            runCatching { answerAnyOffer(driverId) }
                .onFailure { log.debug("{} could not read its offer: {}", driverId, it.message) }
            delay(config.pollInterval)
        }
    }

    /** The driver's app polling its offer, exactly as the kit's D3 screen does. */
    private suspend fun answerAnyOffer(driverId: String) {
        val token = config.token(driverId)
        val response: HttpResponse = client.get(DriverOffers.ForDriver(driverId = driverId)) { token?.let(::bearer) }
        if (response.status != HttpStatusCode.OK) return
        val offer = response.body<OfferView>()
        when (config.behaviour(driverId)) {
            SimulatedBehaviour.IGNORE -> log.debug("{} ignores the offer for {}", driverId, offer.rideId)
            SimulatedBehaviour.ACCEPT -> answer(offer, driverId, DriverDecision.ACCEPT)
            SimulatedBehaviour.DECLINE -> answer(offer, driverId, DriverDecision.DECLINE)
        }
    }

    private suspend fun answer(
        offer: OfferView,
        driverId: String,
        decision: DriverDecision,
    ) {
        val token = config.token(driverId)
        runCatching {
            client.post(DriverOffers.Answer(rideId = offer.rideId)) {
                token?.let(::bearer)
                contentType(ContentType.Application.Json)
                setBody(OfferAnswer(driverId, decision))
            }
        }.onFailure {
            // Two drivers can be polled into answering the same offer, and the second loses; that is
            // the server being right, not the simulator being broken.
            log.debug("{} could not answer {}: {}", driverId, offer.rideId, it.message)
        }
    }

    private fun randomPointNear(
        centre: GeoPoint,
        radiusMetres: Double,
        random: Random,
    ): GeoPoint {
        val dLat = (random.nextDouble() * 2 - 1) * radiusMetres / METRES_PER_DEGREE_LAT
        val dLon =
            (random.nextDouble() * 2 - 1) * radiusMetres / (METRES_PER_DEGREE_LAT * cos(Math.toRadians(centre.lat)))
        return GeoPoint(centre.lat + dLat, centre.lon + dLon)
    }

    private fun moveTowards(
        from: GeoPoint,
        to: GeoPoint,
        metres: Double,
    ): GeoPoint {
        val distance = metresBetween(from, to)
        if (distance <= metres || distance == 0.0) return to
        val fraction = metres / distance
        return GeoPoint(from.lat + (to.lat - from.lat) * fraction, from.lon + (to.lon - from.lon) * fraction)
    }

    private fun metresBetween(
        a: GeoPoint,
        b: GeoPoint,
    ): Double =
        io.github.youndie.shashki.server.common
            .haversineMetres(a, b)

    private companion object {
        const val METRES_PER_DEGREE_LAT = 111_320.0
        const val MILLIS = 1_000.0
        const val MIN_RATING = 4.5
        const val MAX_RATING = 5.0
    }
}

/** One place that writes the header, so a route added to the simulator is authenticated with it. */
private fun HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}
