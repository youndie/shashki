package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The routes, through the same `@Resource` classes a client would build its URLs from. */
class RideRoutesTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `asking for a car parks it at MATCHING, and the driver's accept makes it ASSIGNED`() =
        testApplication {
            application { shashki(PostgresHarness.database) }
            val client = typedClient()

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
            assertEquals(
                HttpStatusCode.NotFound,
                client.get(DriverOffers.ForDriver(driverId = "driver-1")).status,
                "an accepted offer leaves the board",
            )
        }

    @Test
    fun `the rider can cancel while a driver is being asked, and not after`() =
        testApplication {
            application { shashki(PostgresHarness.database) }
            val client = typedClient()
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

    @Test
    fun `an unknown ride is 404`() =
        testApplication {
            application { shashki(PostgresHarness.database) }

            assertEquals(HttpStatusCode.NotFound, typedClient().get(Rides.ById(id = "nope")).status)
        }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
        }
}
