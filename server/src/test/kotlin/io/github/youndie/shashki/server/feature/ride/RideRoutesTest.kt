package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The routes, through the same `@Resource` classes a client would build its URLs from. */
class RideRoutesTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `asking for a car parks it at MATCHING, and the driver's accept makes it ASSIGNED`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            // Candidates come from the geo-index now (B-20), so a ride with nobody online is
            // cancelled before it is offered — correctly. A driver has to be there first, and it
            // gets there the way a real one does: up the position socket.
            parkDriver(client, app, "driver-1", RideClass.ECONOMY)

            val created =
                client.post(Rides()) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        RideRequest(
                            riderId = "rider-1",
                            pickup = GeoPoint(46.0511, 14.5051),
                            dropoff = GeoPoint(46.2237, 14.4576),
                            rideClass = RideClass.ECONOMY,
                            paymentMethodId = "card-4417",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, created.status)
            val ride = created.body<RideView>()
            assertEquals(RideStatus.MATCHING, ride.status)
            assertEquals(null, ride.driverId)
            assertNotNull(ride.quote).let { assertEquals("USD", it.currency) }

            // **R5's numbers while R5 is the screen** (B-73): one car was there, it is the one being
            // asked, and the deadline travels with the clock it was read at rather than alone.
            val searching = assertNotNull(client.get(Rides.ById(id = ride.id)).body<RideView>().search)
            assertEquals(1, searching.carsNearby)
            assertEquals(1, searching.asked)
            assertTrue(searching.offerExpiresAtEpochMs > searching.nowEpochMs, "the offer out has time on it")

            // The driver's app sees the offer the kit draws, and answers it.
            val offer = client.get(DriverOffers.ForDriver(driverId = "driver-1"))
            assertEquals(HttpStatusCode.OK, offer.status)
            assertEquals(ride.id, offer.body<OfferView>().rideId)

            val answered =
                client.post(DriverOffers.Answer(rideId = ride.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(OfferAnswer("driver-1", DriverDecision.ACCEPT))
                }
            assertEquals(HttpStatusCode.OK, answered.status)
            assertEquals(RideStatus.ASSIGNED, answered.body<RideView>().status)
            assertEquals("driver-1", answered.body<RideView>().driverId)

            val read = client.get(Rides.ById(id = ride.id))
            assertEquals(RideStatus.ASSIGNED, read.body<RideView>().status)
            assertNull(read.body<RideView>().search, "a countdown over an assigned ride would be a lie")
            assertEquals(
                HttpStatusCode.NotFound,
                client.get(DriverOffers.ForDriver(driverId = "driver-1")).status,
                "an accepted offer leaves the board",
            )
        }

    @Test
    fun `the rider can cancel while a driver is being asked, and not after`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            parkDriver(client, app, "driver-1", RideClass.ECONOMY)
            val ride =
                client
                    .post(Rides()) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            RideRequest(
                                "rider-1",
                                GeoPoint(46.0511, 14.5051),
                                GeoPoint(46.2237, 14.4576),
                                RideClass.ECONOMY,
                                "card-4417",
                            ),
                        )
                    }.body<RideView>()

            val cancelled = client.post(Rides.Cancel(id = ride.id))
            assertEquals(HttpStatusCode.OK, cancelled.status)
            assertEquals(RideStatus.CANCELLED, cancelled.body<RideView>().status)

            // Cancelling again: the saga is no longer waiting, and that is a 400 rather than a repeat.
            assertEquals(HttpStatusCode.BadRequest, client.post(Rides.Cancel(id = ride.id)).status)
        }

    /**
     * **The answer of a driver who is not the one being asked is refused, and says so.**
     *
     * `DriverAnswerStep` already ignores it — correctly, and completely silently: it resuspends for
     * the driver who *is* offered, and the ride comes back unchanged. Before B-29 that reached the
     * client as `200 OK` carrying somebody else's ride, which is the shape of every "I accepted it
     * and got a trip that was not mine" bug. The tab that was asleep for twenty seconds is the
     * ordinary case here, not the exotic one.
     */
    @Test
    fun `a driver who is not the one being asked cannot take the ride`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            parkDriver(client, app, "driver-1", RideClass.ECONOMY)
            val ride =
                client
                    .post(Rides()) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            RideRequest(
                                "rider-1",
                                PICKUP,
                                GeoPoint(46.2237, 14.4576),
                                RideClass.ECONOMY,
                                "card-4417",
                            ),
                        )
                    }.body<RideView>()
            // The positive control. Without it a 409 could mean "there was no offer at all", which
            // is a different server and the same green tick.
            val offered = client.get(DriverOffers.ForDriver(driverId = "driver-1"))
            assertEquals(HttpStatusCode.OK, offered.status, "nothing was offered, so nothing can be stolen")
            val view = offered.body<OfferView>()
            assertEquals(ride.id, view.rideId)
            // Both ends of the deadline, so the driver's client counts a duration rather than
            // subtracting its own wall clock from a timestamp. See `OfferView`.
            assertTrue(view.expiresAtEpochMs > view.nowEpochMs, "the offer expires before the clock that measured it")

            val stranger =
                client.post(DriverOffers.Answer(rideId = ride.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(OfferAnswer("driver-9", DriverDecision.ACCEPT))
                }

            assertEquals(HttpStatusCode.Conflict, stranger.status, stranger.bodyAsText().take(200))
            // And the ride is untouched: still being offered to the driver who was asked.
            val after = client.get(Rides.ById(id = ride.id)).body<RideView>()
            assertEquals(RideStatus.MATCHING, after.status)
            assertEquals(null, after.driverId)
        }

    @Test
    fun `an unknown ride is 404`() =
        testApplication {
            application { shashki(PostgresHarness.database) }

            assertEquals(HttpStatusCode.NotFound, typedClient().get(Rides.ById(id = "nope")).status)
        }

    /**
     * One driver, online, fifty metres from the pickup — over the socket, not into the index.
     *
     * Returns the open session, and the caller keeps it: **closing the socket is how a driver goes
     * offline**, so a helper that tidied up after itself put the driver in the index and took them
     * straight back out. The first version did exactly that, and the ride was cancelled for want of
     * cars a line later.
     */
    private suspend fun parkDriver(
        client: HttpClient,
        app: Application,
        driverId: String,
        rideClass: RideClass,
    ): DefaultClientWebSocketSession {
        val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
        val at = GeoPoint(PICKUP.lat + 50 / 111_320.0, PICKUP.lon)
        session.send(
            Frame.Text(Json.encodeToString(DriverReport.serializer(), DriverReport(driverId, rideClass, 4.9, at))),
        )
        // The socket is asynchronous, so the frame being sent is not the index having it. Waiting on
        // the index itself is the only honest condition — an earlier version waited on a request
        // that was true before the driver existed, which is a sleep wearing an assertion.
        val index = app.get<DriverIndex>()
        val clock = app.get<PetichClock>()
        awaitTrue("$driverId reaches the index") {
            index.near(PICKUP, rideClass, clock.nowEpochMs()).any { it.driverId == driverId }
        }
        return session
    }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(WebSockets)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        val PICKUP = GeoPoint(46.0511, 14.5051)
    }
}
