package io.github.youndie.shashki.rider.feature.promo

import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.shashki.protocol.DegradationReport
import io.github.youndie.shashki.rider.feature.promo.data.ReportingDegradationSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The near end of the sink, checked against the far end's type.
 *
 * **What can go wrong here is the address and the shape**, and neither is visible from the rider's
 * side alone: `onUnknown` is called by kompot, the report is dropped into a scope, and nothing in
 * this module ever reads the answer. So the request is caught on the way out and decoded with the
 * *server's* `DegradationReport` — the same class the route receives, out of `:protocol`. B-39's
 * point was that a mechanism built at both ends and joined at neither is the recurring defect in
 * this repository; this is the client's half of the join and `DegradationRoutesTest` is the other.
 *
 * **The report is awaited rather than stepped through.** `onUnknown` returns before anything is
 * sent — that is the whole design — and the engine answers on its own dispatcher, so advancing the
 * test scheduler proves nothing: it would return with the list still empty, which is exactly how the
 * first version of this test failed.
 */
class ReportingDegradationSinkTest {
    @Test
    fun `an unknown component leaves as a report the server's own route would accept`() =
        runTest {
            val sent = CompletableDeferred<HttpRequestData>()
            val client =
                client(
                    MockEngine { request ->
                        sent.complete(request)
                        respond("", HttpStatusCode.Accepted)
                    },
                )

            ReportingDegradationSink(client, realWork, screen = "promo")
                .onUnknown(KompotDegradationKind.UNKNOWN_COMPONENT, "earningsTile", drawnAsFallback = true)

            val request = sent.await()
            assertEquals("/api/screens/degradations", request.url.encodedPath, "the report went somewhere else")
            assertEquals(
                DegradationReport("UNKNOWN_COMPONENT", "earningsTile", "promo", drawnAsFallback = true),
                Json.decodeFromString(DegradationReport.serializer(), (request.body as TextContent).text),
                "the server would decode something other than what was sent",
            )
        }

    /**
     * A report that fails must not become a second problem on a screen that already has one — the
     * sink is called from composition, where there is nobody to catch anything. The failure is
     * raised where a real one is, inside the engine; the assertion is that the test finishes at all
     * rather than dying on the exception the sink's `runCatching` is there to swallow.
     */
    @Test
    fun `a report that fails is not a second failure on the screen`() =
        runTest {
            val reached = CompletableDeferred<Unit>()
            val client =
                client(
                    MockEngine {
                        reached.complete(Unit)
                        error("the network is down")
                    },
                )

            ReportingDegradationSink(client, realWork, screen = "promo")
                .onUnknown(KompotDegradationKind.UNKNOWN_COMPONENT, "earningsTile", drawnAsFallback = false)

            reached.await()
        }

    /**
     * **Not the test's own scope**, which is where the second version of this test hung for a
     * minute: the sink launches, the launch waits on an engine that answers on a real dispatcher,
     * and a virtual-time scheduler and a real thread wait for each other. The application's scope is
     * a real one, so the test uses a real one.
     */
    private val realWork = CoroutineScope(Dispatchers.Default)

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(Resources)
            install(ContentNegotiation) { json() }
        }
}
