package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A journey the roads do not reach (B-57).
 *
 * **One condition and one answer.** `/api/routes` and `/api/quotes` map the router's refusal to 422;
 * `POST /api/rides` used to answer **500** for the same point, with GraphHopper's own text about
 * coordinates and a bounding box — because petich runs `ENRICHMENT` before `VALIDATION`, so the
 * quote is taken before the step whose whole job is to refuse this politely ever runs. The refusal
 * moved in front of the saga.
 */
class OutsideTheServiceAreaTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a pickup outside the area is refused before a ride exists`() =
        testApplication {
            application { shashki(PostgresHarness.database) }
            val client = typedClient()
            startApplication()

            val response =
                client.post(Rides()) {
                    contentType(ContentType.Application.Json)
                    setBody(RideRequest(RIDER, ATLANTIC, IN_TOWN, RideClass.ECONOMY, "card-4417"))
                }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, response.bodyAsText())
            assertTrue(
                response.bodyAsText().contains("pickup is outside"),
                "the answer does not say which end of the journey is the problem: ${response.bodyAsText()}",
            )
            // **And no saga was started**, which is the other half: a refusal that left a CANCELLED
            // ride behind would put a journey nobody can take in the rider's own history.
            assertTrue(client.get(Rides(mine = true)).bodyAsText().let { it == "[]" || !it.contains(RIDER) })
        }

    @Test
    fun `a dropoff outside the area is refused too, and says so`() =
        testApplication {
            application { shashki(PostgresHarness.database) }
            val client = typedClient()
            startApplication()

            val response =
                client.post(Rides()) {
                    contentType(ContentType.Application.Json)
                    setBody(RideRequest(RIDER, IN_TOWN, ATLANTIC, RideClass.ECONOMY, "card-4417"))
                }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue(response.bodyAsText().contains("dropoff is outside"), response.bodyAsText())
        }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
            expectSuccess = false
        }

    private companion object {
        const val RIDER = "rider@example.com"

        /** Null island: inside no extract anybody would ship. */
        val ATLANTIC = GeoPoint(0.0, 0.0)
        val IN_TOWN = GeoPoint(46.0511, 14.5051)
    }
}
