package io.github.youndie.shashki.server.feature.promo

import io.github.youndie.shashki.protocol.DegradationReport
import io.github.youndie.shashki.protocol.Degradations
import io.github.youndie.shashki.server.baseModule
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

/**
 * **The number moves.** B-39's criterion is worded that way on purpose: a sink bound at both ends
 * and joined at neither is what B-32 left behind and what four other items in this backlog turned
 * out to be, and "the binding exists" is exactly the evidence that does not distinguish the two.
 *
 * The report is posted as `DegradationReport` — the class the rider's sink encodes, out of
 * `:protocol` — so this and `ReportingDegradationSinkTest` meet on the wire rather than on a string.
 */
class DegradationRoutesTest {
    private val counter = DegradationCounter()

    @Test
    fun `a component the client could not draw is counted, by kind and by type`() =
        withCounter { client ->
            val response: HttpResponse =
                client.post(Degradations()) {
                    contentType(ContentType.Application.Json)
                    setBody(DegradationReport("UNKNOWN_COMPONENT", "earningsTile", "promo", drawnAsFallback = true))
                }

            // 202: the client has said its piece and nothing it draws depends on the answer.
            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals(1, counter.count("UNKNOWN_COMPONENT", "earningsTile"))
            assertEquals(1, counter.total())

            client.post(Degradations()) {
                contentType(ContentType.Application.Json)
                setBody(DegradationReport("UNKNOWN_COMPONENT", "earningsTile", "trip", drawnAsFallback = false))
            }

            assertEquals(2, counter.count("UNKNOWN_COMPONENT", "earningsTile"), "the second client was not counted")
        }

    /**
     * The control, and the reason the counter is keyed rather than a single total: a graph that
     * moved on any report would say a build is broken without saying what to put back, and an
     * assertion against a total would pass over a counter that ignored its key entirely.
     */
    @Test
    fun `a different component is a different counter`() =
        withCounter { client ->
            client.post(Degradations()) {
                contentType(ContentType.Application.Json)
                setBody(DegradationReport("UNKNOWN_ACTION", "openWallet", "promo", drawnAsFallback = false))
            }

            assertEquals(0, counter.count("UNKNOWN_COMPONENT", "earningsTile"))
            assertEquals(1, counter.count("UNKNOWN_ACTION", "openWallet"))
        }

    private fun withCounter(block: suspend (io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application {
                baseModule(listOf(module { single { counter } }))
                routing { degradationRoutes() }
            }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                    install(Resources)
                }
            startApplication()
            block(client)
        }
}
