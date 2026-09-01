package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.GeoPoint
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
    fun `asking for a car returns the ride assigned, and it can be read back by id`() =
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
            assertEquals(RideStatus.ASSIGNED, ride.status)
            assertEquals("driver-1", ride.driverId)
            assertNotNull(ride.quote).let { assertEquals("USD", it.currency) }

            val read = client.get(Rides.ById(id = ride.id))
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals(ride, read.body<RideView>())
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
