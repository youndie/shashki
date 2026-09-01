package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.protocol.Routes
import io.github.youndie.shashki.server.baseModule
import io.github.youndie.shashki.server.common.haversineMetres
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The endpoint, over the L-shaped fixture graph. No database: routing needs none, and a test that
 * started Postgres to answer a question about roads would be slower and no more truthful.
 */
class RouteRoutesTest {
    @Test
    fun `two points come back as a road, in metres and seconds`() =
        withGraph { client ->
            val response: HttpResponse =
                client.post(Routes()) {
                    contentType(ContentType.Application.Json)
                    setBody(RouteRequest(WEST_END, NORTH_END))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val route = response.body<RouteView>()
            assertTrue(route.distanceMetres > 0 && route.durationSeconds > 0)
            assertTrue(route.geometry.size >= 3, "two points would be the straight-line stand-in's answer")
            assertTrue(
                route.distanceMetres > haversineMetres(WEST_END, NORTH_END) * 1.2,
                "the endpoint answered the hypotenuse: ${route.distanceMetres} m",
            )
        }

    /**
     * 422 and not 400. The request is well formed and the server understood every field of it; what
     * is missing is a road. Answering 400 would tell the client to fix its message.
     */
    @Test
    fun `a point with no road near it is unprocessable rather than malformed`() =
        withGraph { client ->
            val response: HttpResponse =
                client.post(Routes()) {
                    contentType(ContentType.Application.Json)
                    setBody(RouteRequest(WEST_END, GeoPoint(47.5000, 19.0400)))
                }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }

    /**
     * **The endpoint and the quote are the same road**, which is the half of B-23's second criterion
     * that can be checked without a saga: `QuoteStep` prices whatever `RouteEstimator` Koin holds,
     * and this is that object. A rider shown 3.8 km beside a fare computed from 2.7 km would be
     * looking at two different journeys.
     */
    @Test
    fun `the price is computed from the road the endpoint returns`() =
        withGraph { client ->
            val response: HttpResponse =
                client.post(Routes()) {
                    contentType(ContentType.Application.Json)
                    setBody(RouteRequest(WEST_END, NORTH_END))
                }
            val route = response.body<RouteView>()

            val quote = Pricing().quote(WEST_END, RideClass.ECONOMY, estimator.estimate(WEST_END, NORTH_END))

            assertEquals(route.distanceMetres, quote.distanceMetres)
            assertEquals(route.durationSeconds, quote.durationSeconds)
        }

    private fun withGraph(block: suspend (io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application {
                baseModule(listOf(module { single<RouteEstimator> { estimator } }))
                routing { routeRoutes() }
            }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                    install(Resources)
                }
            startApplication()
            block(client)
        }

    private companion object {
        /** One graph for the class: importing it per test would be the slowest thing in the suite. */
        val estimator = FixtureGraph.estimator

        val WEST_END = GeoPoint(46.0500, 14.5000)
        val NORTH_END = GeoPoint(46.0700, 14.5200)
    }
}
