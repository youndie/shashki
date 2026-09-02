package io.github.youndie.shashki.server.feature.events

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideHistoryView
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.protocol.TripAdvance
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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole seam, against a broker that is actually running.
 *
 * **This is the test the item exists for.** Every part of it was already built and none of them was
 * joined: the saga writes an outbox row in its own transaction (B-11), the relay reads and retries
 * (petich), and the far end wrote a log line. What this asserts is that a ride's events leave the
 * database, cross a broker, come back through a consumer that shares nothing with the saga, and can
 * be read by somebody who was never told what happened.
 *
 * ```bash
 * docker compose -f docker/compose.yaml up -d booblik
 * SHASHKI_BOOBLIK=127.0.0.1:19092 ./gradlew :server:test --tests '*OverBooblik*'
 * ```
 */
class RideEventsOverBooblikTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a ride's events cross the broker and come back as its history`() =
        testApplication {
            val broker = System.getenv(EventsConfig.ADDRESS_VARIABLE)
            assumeTrue(!broker.isNullOrBlank(), "no ${EventsConfig.ADDRESS_VARIABLE}: this test needs a booblik")

            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            val ride = assignedRide(client, app)
            // Assigned, so the order saga has written `ride.assigned` into the outbox. Nothing in
            // this test tells the projection about it — the only path is the broker.
            assertEquals(RideStatus.ASSIGNED, ride.status)

            awaitTrue("ride.assigned reaches the projection through booblik") {
                client.get(Rides.History(id = ride.id)).body<RideHistoryView>().events.any {
                    it.type == "ride.assigned"
                }
            }

            // And the second saga's event, so the history is a sequence rather than one record.
            for (state in listOf(
                RideStatus.ARRIVING,
                RideStatus.ARRIVED,
                RideStatus.IN_PROGRESS,
                RideStatus.COMPLETED,
            )) {
                client.post(DriverRides.Advance(rideId = ride.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(TripAdvance(DRIVER, state))
                }
            }

            awaitTrue("ride.settled follows it") {
                client.get(Rides.History(id = ride.id)).body<RideHistoryView>().events.any {
                    it.type == "ride.settled"
                }
            }

            val history = client.get(Rides.History(id = ride.id)).body<RideHistoryView>()
            assertEquals(listOf("ride.assigned", "ride.settled"), history.events.map { it.type })
            assertTrue(
                history.events.zipWithNext().all { (a, b) -> a.offset < b.offset },
                "the broker's offsets are not increasing: ${history.events}",
            )

            // The control: another ride's history is empty, so what came back was keyed rather than
            // whatever the topic happened to hold.
            assertEquals(emptyList(), client.get(Rides.History(id = "somebody-else")).body<RideHistoryView>().events)
        }

    private suspend fun assignedRide(
        client: HttpClient,
        app: Application,
    ): RideView {
        parkDriver(client, app)
        val ride =
            client
                .post(Rides()) {
                    contentType(ContentType.Application.Json)
                    setBody(RideRequest("rider-1", PICKUP, DROPOFF, RideClass.ECONOMY, "card-4417"))
                }.body<RideView>()
        return client
            .post(DriverOffers.Answer(rideId = ride.id)) {
                contentType(ContentType.Application.Json)
                setBody(OfferAnswer(DRIVER, DriverDecision.ACCEPT))
            }.body()
    }

    private suspend fun parkDriver(
        client: HttpClient,
        app: Application,
    ) {
        val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
        val at = GeoPoint(PICKUP.lat + 50 / 111_320.0, PICKUP.lon)
        session.send(
            Frame.Text(
                Json.encodeToString(DriverReport.serializer(), DriverReport(DRIVER, RideClass.ECONOMY, 4.9, at)),
            ),
        )
        val index = app.get<DriverIndex>()
        val clock = app.get<PetichClock>()
        awaitTrue("$DRIVER reaches the index") {
            index.near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).any { it.driverId == DRIVER }
        }
    }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(WebSockets)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        const val DRIVER = "driver-1"
        val PICKUP = GeoPoint(46.0511, 14.5051)
        val DROPOFF = GeoPoint(46.2237, 14.4576)
    }
}
