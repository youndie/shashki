package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
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
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.SimpleEnrichedPayload
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * B-12's cascade, driven by simulated drivers rather than by a list written in the test.
 *
 * This is the acceptance the offer item could not have: `OfferCascadeTest` proves the saga's shape
 * against three known candidates, and this proves the same shape survives contact with matching —
 * the candidates come from the index, the answers come over HTTP, and nothing in the saga knows the
 * difference.
 */
class SimulatedCascadeTest {
    private val pickup = GeoPoint(46.0511, 14.5051)

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `drivers who all decline exhaust the cascade and the rider is told there are no cars`() =
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
            val jobs =
                DriverSimulator(
                    client,
                    SimulatorConfig(
                        drivers = 3,
                        centre = pickup,
                        radiusMetres = 500.0,
                        reportInterval = 200.milliseconds,
                        pollInterval = 50.milliseconds,
                        behaviour = { SimulatedBehaviour.DECLINE },
                        // All three in one class, or the cascade for a given request is one offer
                        // long — which is how this test first passed while proving nothing.
                        rideClass = { RideClass.COMFORT },
                    ),
                ).start(app)
            try {
                awaitTrue("three comfort drivers are online") {
                    index.near(pickup, RideClass.COMFORT, clock.nowEpochMs()).size == 3
                }

                val ride =
                    client
                        .post(Rides()) {
                            contentType(ContentType.Application.Json)
                            setBody(RideRequest("rider-1", pickup, north(4_000.0), RideClass.COMFORT, "card-4417"))
                        }.body<RideView>()

                awaitTrue("every candidate declines and the ride is cancelled") {
                    client.get(Rides.ById(id = ride.id)).body<RideView>().status == RideStatus.CANCELLED
                }

                val finished = client.get(Rides.ById(id = ride.id)).body<RideView>()
                assertEquals(RideStatus.CANCELLED, finished.status)
                assertEquals(null, finished.driverId, "nobody took it")
                assertTrue(app.get<DriverReservations>().all().isEmpty(), "no driver is left reserved")

                // **The evidence that a cascade happened, and not that the ride was cancelled for
                // want of any driver at all.** Both end CANCELLED with no reservations, so without
                // this the test would pass against an empty index — which is exactly how it passed
                // the first time it was run. The saga records which driver was asked and how many
                // had been asked before; a real cascade leaves a `sim-` driver and an attempt count
                // above zero.
                val saga = checkNotNull(app.get<SagaStorage>().petiches.findById(ride.id))
                val enriched = (saga.enrichedPayload as SimpleEnrichedPayload).data
                assertTrue(
                    enriched[Enriched.OFFER_DRIVER]?.startsWith("sim-") == true,
                    "the last offer went to a simulated driver, not a stub: $enriched",
                )
                assertEquals(
                    2,
                    enriched[Enriched.OFFER_ATTEMPT]?.toIntOrNull(),
                    "all three candidates were asked in turn: $enriched",
                )
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

    private fun north(metres: Double) = GeoPoint(pickup.lat + metres / 111_320.0, pickup.lon)

    private fun ApplicationTestBuilder.simulatorClient(): HttpClient =
        createClient {
            install(WebSockets)
            install(Resources)
            install(ContentNegotiation) { json() }
        }
}
