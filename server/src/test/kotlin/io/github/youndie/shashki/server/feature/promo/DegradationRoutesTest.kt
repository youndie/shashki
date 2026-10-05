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
import io.ktor.http.content.TextContent
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
                    setBody(
                        DegradationReport("UNKNOWN_COMPONENT", "earningsTile", "promo", outcome = "SERVER_FALLBACK"),
                    )
                }

            // 202: the client has said its piece and nothing it draws depends on the answer.
            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals(1, counter.count("UNKNOWN_COMPONENT", "earningsTile"))
            assertEquals(1, counter.total())

            client.post(Degradations()) {
                contentType(ContentType.Application.Json)
                setBody(DegradationReport("UNKNOWN_COMPONENT", "earningsTile", "trip", outcome = "NOTHING"))
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
                setBody(DegradationReport("UNKNOWN_ACTION", "openWallet", "promo", outcome = "NOTHING"))
            }

            assertEquals(0, counter.count("UNKNOWN_COMPONENT", "earningsTile"))
            assertEquals(1, counter.count("UNKNOWN_ACTION", "openWallet"))
        }

    /**
     * **The outcome survives the trip, server side included** (#43). The two reports above are one
     * component and two outcomes: the total by kind and type is two, and the split says which client
     * saw the server's equivalent and which saw a hole — the distinction kompot 0.38 introduced.
     */
    @Test
    fun `what the client drew instead is counted apart`() =
        withCounter { client ->
            for (outcome in listOf("SERVER_FALLBACK", "NOTHING", "NOTHING")) {
                client.post(Degradations()) {
                    contentType(ContentType.Application.Json)
                    setBody(DegradationReport("UNKNOWN_COMPONENT", "box", "promo", outcome = outcome))
                }
            }

            assertEquals(3, counter.count("UNKNOWN_COMPONENT", "box"))
            assertEquals(1, counter.count("UNKNOWN_COMPONENT", "box", "SERVER_FALLBACK"))
            assertEquals(2, counter.count("UNKNOWN_COMPONENT", "box", "NOTHING"))
            assertEquals(0, counter.count("UNKNOWN_COMPONENT", "box", "PLACEHOLDER"))
        }

    /**
     * **A bundle built before #43 is still heard.** It sends `drawnAsFallback` and no outcome; the
     * server decodes strictly, so without the field kept on the DTO this answer would be a 400 and
     * the report — the one thing the sink must never lose — would be dropped by the server instead.
     * Written as raw JSON, because that is what the older bundle's bytes are.
     */
    @Test
    fun `a report from a bundle older than the outcome is counted as unreported`() =
        withCounter { client ->
            val response =
                client.post(Degradations()) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        TextContent(
                            """{"kind":"UNKNOWN_COMPONENT","componentType":"box","screen":"promo","drawnAsFallback":true}""",
                            ContentType.Application.Json,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals(1, counter.count("UNKNOWN_COMPONENT", "box", DegradationReport.OUTCOME_UNREPORTED))
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
