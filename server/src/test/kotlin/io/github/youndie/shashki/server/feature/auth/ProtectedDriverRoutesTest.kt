package io.github.youndie.shashki.server.feature.auth

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DRIVER_TICKET_QUERY
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.TripAdvance
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.dispatch.DriverTickets
import io.github.youndie.shashki.server.dispatch.DroppedFrames
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import ru.workinprogress.oidc.OidcConfig
import ru.workinprogress.petich.PetichClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.github.youndie.shashki.protocol.DriverTickets as DriverTicketsResource

/**
 * The driver's four routes, behind the driver's token (B-52).
 *
 * **What was open here was not a nuisance, it was the money.** Anybody who knew a driver's id could
 * read the offer waiting for them, accept it, and advance the trip to `COMPLETED` — which captures
 * the rider's hold. `endpoint-driver.md` said "public, temporarily, and the hole is named"; this is
 * the test that the hole is shut.
 *
 * The 401s need no reachable provider: a request with no `Authorization` header is refused before
 * anything is verified, which is why these run everywhere. What a real token buys is
 * `ProtectedRidesTest`'s subject and needs the stand.
 */
class ProtectedDriverRoutesTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `every driver route refuses a request that carries no token`() =
        testApplication {
            application { shashki(PostgresHarness.database, oidc = unreachableProvider()) }
            val client = typedClient()
            startApplication()

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get(DriverOffers.ForDriver(driverId = DRIVER)).status,
                "the offer waiting for a driver is readable by anybody who knows their id",
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client
                    .post(DriverOffers.Answer(rideId = "ride-1")) {
                        contentType(ContentType.Application.Json)
                        setBody(OfferAnswer(DRIVER, DriverDecision.ACCEPT))
                    }.status,
                "anybody can accept a ride on a driver's behalf",
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client
                    .post(DriverRides.Advance(rideId = "ride-1")) {
                        contentType(ContentType.Application.Json)
                        setBody(TripAdvance(DRIVER, RideStatus.COMPLETED))
                    }.status,
                "anybody can finish a trip, which captures the rider's hold",
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post(DriverTicketsResource()).status,
                "the socket's ticket is mintable without a token",
            )
        }

    /**
     * The socket's own refusal, which is a close rather than a status.
     *
     * **There is no 401 to send once an upgrade has completed**, so the server closes with a policy
     * violation before reading a frame. What the test can see is that the connection does not stay
     * open and nothing reached the index.
     */
    @Test
    fun `the position socket refuses an upgrade with no ticket`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database, oidc = unreachableProvider())
            }
            val client = typedClient()
            startApplication()

            val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
            // **Nothing is sent, and that is the fix rather than the test being lazy.** Sending into
            // a socket the server has already closed throws `CancellationException` from the
            // client's own channel — a race between the close and the write that made this test
            // flaky. What is being asserted is the close, and the close needs no frame.
            assertTrue(session.incoming.receiveCatching().isFailure, "the socket stayed open")

            assertTrue(
                app.get<DriverIndex>().near(PICKUP, RideClass.ECONOMY, app.get<PetichClock>().nowEpochMs()).isEmpty(),
                "a socket with no ticket put a driver on the map",
            )
        }

    /**
     * **A frame for somebody else is dropped and counted, not relabelled** — the one place the token
     * is compared with the claimed id rather than replacing it. Rewriting the id would file another
     * driver's position under the connected one, which is worse than losing it, and a count is what
     * makes a client whose id and token disagree visible rather than merely absent.
     *
     * The ticket is minted through the graph rather than through the route: the route needs a real
     * token and this test is about what the socket does with the ticket, not about who may have one.
     */
    @Test
    fun `a position frame for another driver is dropped and counted`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database, oidc = unreachableProvider())
            }
            val client = typedClient()
            startApplication()

            val ticket = app.get<DriverTickets>().mint(DRIVER)
            val session = client.webSocketSession("$DRIVER_POSITIONS_PATH?$DRIVER_TICKET_QUERY=$ticket")
            session.send(Frame.Text(Json.encodeToString(DriverReport.serializer(), report("driver-9"))))
            session.send(Frame.Text(Json.encodeToString(DriverReport.serializer(), report(DRIVER))))

            val index = app.get<DriverIndex>()
            val clock = app.get<PetichClock>()
            awaitTrue("the signed-in driver reaches the index") {
                index.near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).any { it.driverId == DRIVER }
            }
            assertTrue(
                index.near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).none { it.driverId == "driver-9" },
                "a frame for another driver was indexed",
            )
            assertEquals(1, app.get<DroppedFrames>().total())

            // **And exactly one acknowledgement comes back, for the frame that was kept** (B-54).
            // The driver's screen counts these, so a frame the server threw away must produce
            // nothing: a count that rises for a refused frame is the failure the count exists to
            // show, hidden by the count. Two frames went out; one is the whole answer.
            val acknowledged = Json.decodeFromString(DriverReport.serializer(), session.incoming.receiveText())
            assertEquals(DRIVER, acknowledged.driverId, "the socket acknowledged a frame it dropped")
            assertNull(
                withTimeoutOrNull(ACK_QUIET_MS) { session.incoming.receive() },
                "a second acknowledgement arrived for a frame the index refused",
            )
        }

    /** A ticket is spent when it is used, which is what stops a logged URL being a second socket. */
    @Test
    fun `a ticket works once`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database, oidc = unreachableProvider())
            }
            startApplication()

            val tickets = app.get<DriverTickets>()
            val ticket = tickets.mint(DRIVER)

            assertEquals(DRIVER, tickets.redeem(ticket))
            assertNull(tickets.redeem(ticket), "a ticket was redeemed twice")
            assertNull(tickets.redeem("not-a-ticket"))
        }

    private suspend fun ReceiveChannel<Frame>.receiveText(): String = (receive() as Frame.Text).readText()

    private fun report(driverId: String) = DriverReport(driverId, RideClass.ECONOMY, RATING, PICKUP)

    private fun unreachableProvider() = OidcConfig(url = "http://127.0.0.1:1", realm = "shashki", clientId = "rider")

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(WebSockets)
            install(ContentNegotiation) { json() }
            // The statuses are the subject of this test, so they must not be thrown before it sees
            // them.
            expectSuccess = false
        }

    private companion object {
        const val DRIVER = "driver-1"

        /** Long enough for a second frame to arrive if the server were going to send one. */
        const val ACK_QUIET_MS = 500L
        const val RATING = 4.9
        val PICKUP = GeoPoint(46.0511, 14.5051)
    }
}
