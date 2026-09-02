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
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Why a ride was cancelled, all the way to the wire (B-58).
 *
 * **The mechanism was written at both ends and joined at neither** — the fourth time this repository
 * has found that shape. `ServiceAreaStep` and `OfferStep` each refuse with a sentence,
 * `RideView.cancellationReason` carries one and `toRideView` reads `data["rejection"]`; nothing
 * wrote that key, so every cancelled ride came back with `null` and a rider was told a ride was
 * cancelled and never why.
 *
 * The reason cannot be written from inside the saga: `Reject` and `Compensate` take one and petich
 * keeps neither, and only the three results that are *not* refusals carry an `EnrichedPayload`. So it
 * is written where `process` returns, which is what this asserts end to end.
 */
class CancellationReasonTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    /** Nobody is online, so the cascade has nobody to ask. The kit's R5·a, as a sentence. */
    @Test
    fun `a ride with no cars says so`() =
        testApplication {
            application { shashki(PostgresHarness.database) }
            val client = typedClient()
            startApplication()

            val ride: RideView =
                client
                    .post(Rides()) {
                        contentType(ContentType.Application.Json)
                        setBody(RideRequest(RIDER, PICKUP, DROPOFF, RideClass.ECONOMY, "card-4417"))
                    }.body()

            assertEquals(RideStatus.CANCELLED, ride.status)
            assertEquals("no cars nearby", ride.cancellationReason, "a rider is told nothing about why")
        }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        const val RIDER = "rider@example.com"
        val PICKUP = GeoPoint(46.0511, 14.5051)
        val DROPOFF = GeoPoint(46.0620, 14.5350)
    }
}
