package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DRIVER_TICKET_QUERY
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The class a driver drives is the record's (B-63).
 *
 * **A driver who tells the server their own class chooses which offers they are eligible for.** The
 * position frame has carried `rideClass` since the socket existed and the server believed it, which
 * is the security half of B-52's remainder: a signed-in driver could claim `BUSINESS` and be offered
 * the fares that go with it. The record ends that with no new check — the claim is simply not read.
 */
class TheDriversOwnClassTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a frame claiming another class is indexed as the record's`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = createClient { install(WebSockets) }
            startApplication()

            val ticket = app.get<DriverTickets>().mint(SEEDED_DRIVER)
            val session = client.webSocketSession("$DRIVER_POSITIONS_PATH?$DRIVER_TICKET_QUERY=$ticket")
            // The seed says ECONOMY; the frame says otherwise.
            session.send(
                Frame.Text(
                    Json.encodeToString(
                        DriverReport.serializer(),
                        DriverReport(SEEDED_DRIVER, RideClass.BUSINESS, RATING, PICKUP),
                    ),
                ),
            )

            val index = app.get<DriverIndex>()
            val clock = app.get<PetichClock>()
            awaitTrue("the driver never reached the index") {
                index.near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).any { it.driverId == SEEDED_DRIVER }
            }
            assertTrue(
                index.near(PICKUP, RideClass.BUSINESS, clock.nowEpochMs()).none { it.driverId == SEEDED_DRIVER },
                "the frame's claim put a driver in a class the record does not give them",
            )
        }

    /** A driver the server has never heard of is not a candidate, and the log says which one. */
    @Test
    fun `a driver with no record is not indexed at all`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = createClient { install(WebSockets) }
            startApplication()

            val ticket = app.get<DriverTickets>().mint("nobody@example.com")
            val session = client.webSocketSession("$DRIVER_POSITIONS_PATH?$DRIVER_TICKET_QUERY=$ticket")
            session.send(
                Frame.Text(
                    Json.encodeToString(
                        DriverReport.serializer(),
                        DriverReport("nobody@example.com", RideClass.ECONOMY, RATING, PICKUP),
                    ),
                ),
            )

            val clock = app.get<PetichClock>()
            repeat(REPORTS_TO_WAIT) { app.get<DriverIndex>() }
            assertTrue(
                app.get<DriverIndex>().near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).isEmpty(),
                "a driver with no record became a candidate",
            )
        }

    private companion object {
        /** `V4__drivers.sql` seeds this one, because there is no registration to create it. */
        const val SEEDED_DRIVER = "driver-1"
        const val RATING = 4.9
        const val REPORTS_TO_WAIT = 3
        val PICKUP = GeoPoint(46.0511, 14.5051)
    }
}
