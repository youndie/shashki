package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.server.common.haversineMetres
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The saga's first phase prices the road, and this is the test that says so.**
 *
 * ENRICHMENT computes the quote from whatever `RouteEstimator` the graph holds, which is exactly why
 * it is worth checking rather than assuming: the binding is one line, the stand-in is still in the
 * codebase for the saga tests, and a rider shown a fare from a straight line beside an ETA from a
 * road would see two numbers about two different journeys.
 */
class QuoteOnRoadsTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `the ride's quote is the same road the endpoint draws`() =
        testApplication {
            application { shashki(PostgresHarness.database, routeEstimator = FixtureGraph.estimator) }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                    install(Resources)
                }
            startApplication()

            val route: RouteView =
                client
                    .post(
                        io.github.youndie.shashki.protocol
                            .Routes(),
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody(RouteRequest(WEST_END, NORTH_END))
                    }.body()

            val ride: RideView =
                client
                    .post(Rides()) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            RideRequest(
                                riderId = "rider-1",
                                pickup = WEST_END,
                                dropoff = NORTH_END,
                                rideClass = RideClass.COMFORT,
                                paymentMethodId = "card-4417",
                            ),
                        )
                    }.body()

            val quote = requireNotNull(ride.quote) { "the ride came back without a quote: $ride" }
            assertEquals(route.distanceMetres, quote.distanceMetres, "the fare was priced on a different road")
            assertEquals(route.durationSeconds, quote.durationSeconds, "the ETA came from a different road")
            // And it is a road rather than the stand-in agreeing by coincidence.
            assertTrue(
                quote.distanceMetres > haversineMetres(WEST_END, NORTH_END) * 1.2,
                "${quote.distanceMetres} m is the straight line, so this passed with routing switched off",
            )
        }

    private companion object {
        val WEST_END = GeoPoint(46.0500, 14.5000)
        val NORTH_END = GeoPoint(46.0700, 14.5200)
    }
}
