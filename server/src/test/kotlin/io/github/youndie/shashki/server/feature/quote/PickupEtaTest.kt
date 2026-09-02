package io.github.youndie.shashki.server.feature.quote

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quotes
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.feature.route.FixtureGraph
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wait a rider is shown, and where it comes from.
 *
 * **The fixture graph is an L and that is the whole reason this test can prove anything.** Four
 * nodes: the pickup at the west end, a corner two thirds of the way east, and the north end above
 * it. A car at the corner drives one straight kilometre to the pickup; a car at the north end has to
 * come south and then west, and the straight line between it and the pickup — the hypotenuse — is
 * markedly shorter than the road. So a wait computed from the road and a wait computed from the
 * index's own straight-line distance are two different numbers, and the ratio between the two
 * classes says which one is being shown.
 */
class PickupEtaTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    /**
     * B-31's third criterion. **Two classes, two distances, and a ratio the map decides.**
     *
     * A constant would make them equal. A wait taken from the index's straight-line metres would put
     * the ratio near 3.5, because the hypotenuse is what a straight line measures. The road makes it
     * near 4.9, and the assertion sits between the two.
     */
    @Test
    fun `two classes at different distances get different waits, and the difference is the road`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database, routeEstimator = FixtureGraph.estimator)
            }
            val client = typedClient()
            startApplication()

            parkDriver(client, app, "near-economy", RideClass.ECONOMY, MIDPOINT)
            parkDriver(client, app, "far-comfort", RideClass.COMFORT, NORTH_END)

            val quotes: QuotesView =
                client
                    .post(Quotes()) {
                        contentType(ContentType.Application.Json)
                        setBody(RouteRequest(PICKUP, NORTH_END))
                    }.body()

            val economy = assertNotNull(quotes.classes.first { it.rideClass == RideClass.ECONOMY }.pickupEtaSeconds)
            val comfort = assertNotNull(quotes.classes.first { it.rideClass == RideClass.COMFORT }.pickupEtaSeconds)

            assertTrue(economy > 0, "a car one kilometre away arrives in no time at all")
            assertTrue(comfort > economy, "the far car was not slower: economy $economy, comfort $comfort")
            val ratio = comfort.toDouble() / economy
            assertTrue(
                ratio > ROAD_RATIO_FLOOR,
                "the ratio is $ratio — a straight line would give about $STRAIGHT_LINE_RATIO, the road about $ROAD_RATIO",
            )
        }

    /** B-31's second criterion: a class nobody is driving says so rather than guessing. */
    @Test
    fun `a class with no candidate has no wait at all`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database, routeEstimator = FixtureGraph.estimator)
            }
            val client = typedClient()
            startApplication()

            parkDriver(client, app, "only-economy", RideClass.ECONOMY, MIDPOINT)

            val quotes: QuotesView =
                client
                    .post(Quotes()) {
                        contentType(ContentType.Application.Json)
                        setBody(RouteRequest(PICKUP, NORTH_END))
                    }.body()

            // The price is arithmetic and answers for all three; the wait is a fact about the city.
            assertEquals(3, quotes.classes.size)
            assertTrue(quotes.classes.all { it.quote.amountCents > 0 }, "a class lost its price")
            assertNotNull(quotes.classes.first { it.rideClass == RideClass.ECONOMY }.pickupEtaSeconds)
            assertNull(quotes.classes.first { it.rideClass == RideClass.COMFORT }.pickupEtaSeconds)
            assertNull(quotes.classes.first { it.rideClass == RideClass.BUSINESS }.pickupEtaSeconds)
        }

    /**
     * The control. **With nobody online at all every wait is absent** — which is what says the
     * numbers above came from the drivers rather than from the journey being priced.
     */
    @Test
    fun `with nobody online there is no wait for any class`() =
        testApplication {
            application { shashki(PostgresHarness.database, routeEstimator = FixtureGraph.estimator) }
            val client = typedClient()
            startApplication()

            val quotes: QuotesView =
                client
                    .post(Quotes()) {
                        contentType(ContentType.Application.Json)
                        setBody(RouteRequest(PICKUP, NORTH_END))
                    }.body()

            assertTrue(quotes.classes.all { it.pickupEtaSeconds == null }, "${quotes.classes}")
        }

    /** One driver, online, at [at] — over the socket, because that is how a driver goes online. */
    private suspend fun parkDriver(
        client: HttpClient,
        app: Application,
        driverId: String,
        rideClass: RideClass,
        at: GeoPoint,
    ) {
        val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
        session.send(
            Frame.Text(Json.encodeToString(DriverReport.serializer(), DriverReport(driverId, rideClass, 4.9, at))),
        )
        val index = app.get<DriverIndex>()
        val clock = app.get<PetichClock>()
        awaitTrue("$driverId reaches the index") {
            index.near(PICKUP, rideClass, clock.nowEpochMs()).any { it.driverId == driverId }
        }
    }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(WebSockets)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        /** `n1` of the fixture graph: the west end, and the pickup. */
        val PICKUP = GeoPoint(46.0500, 14.5000)

        /** `n2`: 774 m due east of the pickup, on the same way. */
        val MIDPOINT = GeoPoint(46.0500, 14.5100)

        /** `n4`: north of the corner. Two legs from the pickup by road, one hypotenuse by air. */
        val NORTH_END = GeoPoint(46.0700, 14.5200)

        /** 3 772 m of road against 774 m — what the router should be measuring. */
        const val ROAD_RATIO = 4.9

        /** 2 710 m of hypotenuse against 774 m — what the index measures, and what this must not be. */
        const val STRAIGHT_LINE_RATIO = 3.5

        /** Between the two, so the assertion tells them apart rather than merely ordering them. */
        const val ROAD_RATIO_FLOOR = 4.2
    }
}
