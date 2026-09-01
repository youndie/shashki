package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
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
import kotlin.test.assertTrue

/**
 * Matching end to end: positions go up the real socket and the query reads the real index.
 *
 * The ordering *rules* are stated exactly in [DriverIndexTest], where the drivers stand still at
 * known places. What this file adds is that the pipeline is connected — a socket the simulator can
 * speak and an index the saga's candidate query reads — which is the half a unit test cannot show.
 */
class MatchingTest {
    private val pickup = GeoPoint(46.0511, 14.5051)

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `twenty simulated drivers reach the index, and a driver parked next door is offered first`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = simulatorClient()
            startApplication()

            val index = app.get<DriverIndex>()
            val clock = app.get<PetichClock>()
            // The Application is the simulator's scope, so its drivers stop when the test's server does.
            val jobs = DriverSimulator(client, SimulatorConfig(drivers = 20, centre = pickup)).start(app)
            try {
                awaitTrue("twenty simulated drivers report in") { index.onlineCount(clock.nowEpochMs()) == 20 }

                // One known driver, fifty metres away, over the same socket the simulator uses.
                val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
                session.send(Frame.Text(report(DriverReport("next-door", RideClass.COMFORT, 4.6, north(50.0)))))
                awaitTrue("the known driver is indexed") {
                    index.near(pickup, RideClass.COMFORT, clock.nowEpochMs()).any { it.driverId == "next-door" }
                }

                val candidates = index.near(pickup, RideClass.COMFORT, clock.nowEpochMs())
                assertEquals(
                    "next-door",
                    candidates.first().driverId,
                    "the nearest online driver of the class is first",
                )
                assertTrue(
                    candidates.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres },
                    "candidates are sorted by distance: ${candidates.map { it.distanceMetres }}",
                )
                assertTrue(candidates.all { it.driverId != "next-door" || it.rating == 4.6 })
                session.close()
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

    @Test
    fun `a restarted process starts with an empty index and fills from the stream`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = simulatorClient()
            startApplication()

            val index = app.get<DriverIndex>()
            val clock = app.get<PetichClock>()

            // This application *is* the restarted one: whatever a previous process knew is gone,
            // because there is nowhere it could have been written down — no table, no repository.
            assertEquals(emptyList(), index.near(pickup, RideClass.ECONOMY, clock.nowEpochMs()))

            val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
            session.send(Frame.Text(report(DriverReport("returning", RideClass.ECONOMY, 4.9, north(100.0)))))

            awaitTrue("one reporting interval refills the index") {
                index.near(pickup, RideClass.ECONOMY, clock.nowEpochMs()).map { it.driverId } == listOf("returning")
            }
            session.close()
        }

    private fun report(report: DriverReport): String = Json.encodeToString(DriverReport.serializer(), report)

    private fun north(metres: Double) = GeoPoint(pickup.lat + metres / METRES_PER_DEGREE_LAT, pickup.lon)

    private fun ApplicationTestBuilder.simulatorClient(): HttpClient =
        createClient {
            install(WebSockets)
            install(Resources)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        const val METRES_PER_DEGREE_LAT = 111_320.0
    }
}
