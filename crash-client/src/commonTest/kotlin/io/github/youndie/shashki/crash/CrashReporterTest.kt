package io.github.youndie.shashki.crash

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReporterTest {
    /** B-10's criterion, minus the client that does not exist yet: the build identifier is on it. */
    @Test
    fun `a report reaches the ingest with the build identifier on it`() =
        runTest {
            val sent = mutableListOf<HttpRequestData>()
            val reporter = reporter(accepting(sent))

            val ok = reporter.report(IllegalStateException("the map surface was never bound"))

            assertTrue(ok)
            val request = sent.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://katcher.example/api/reports", request.url.toString())

            val report = decode(request)
            assertEquals("2026.09.02-1a2b3c4", report.release, "no build identifier: the report names no build")
            assertEquals("shashki-rider", report.appKey)
            assertEquals("the map surface was never bound", report.message)
            assertTrue("IllegalStateException" in report.stacktrace, "the stack trace lost the type")
            assertEquals("production", report.environment)
        }

    /** `window.onerror` hands over strings, never a `Throwable`; that path carries the build too. */
    @Test
    fun `a browser error reported as two strings carries the same identifiers`() =
        runTest {
            val sent = mutableListOf<HttpRequestData>()

            reporter(accepting(sent)).report("Script error.", "at main.wasm:0:0", mapOf("url" to "/rider"))

            val report = decode(sent.single())
            assertEquals("Script error.", report.message)
            assertEquals("at main.wasm:0:0", report.stacktrace)
            assertEquals("2026.09.02-1a2b3c4", report.release)
            assertEquals(mapOf("url" to "/rider"), report.context)
        }

    /**
     * **202 and only 202.** katcher queues the report and answers `Accepted`; a reporter that took
     * any 2xx would call a 200 from a proxy, a redirect target or a captive portal a delivery.
     */
    @Test
    fun `only Accepted counts as delivered`() =
        runTest {
            for (status in listOf(HttpStatusCode.OK, HttpStatusCode.NoContent, HttpStatusCode.Created)) {
                assertFalse(reporter(answering(status)).report(RuntimeException("x")), "$status was taken as sent")
            }
            assertTrue(reporter(answering(HttpStatusCode.Accepted)).report(RuntimeException("x")))
        }

    /** An unknown key is 401 — katcher's own answer — and it is a failure, not an exception. */
    @Test
    fun `an unknown app key is reported as not delivered rather than thrown`() =
        runTest {
            assertFalse(reporter(answering(HttpStatusCode.Unauthorized)).report(RuntimeException("x")))
        }

    /**
     * The property that matters most and is easiest to lose: this runs inside an uncaught-exception
     * handler, so a reporter that threw would replace the crash being reported with its own.
     */
    @Test
    fun `a dead network does not throw out of the reporter`() =
        runTest {
            val exploding = HttpClient(MockEngine { throw RuntimeException("connection refused") })

            assertFalse(reporter(exploding).report(RuntimeException("x")))
        }

    /** Empty maps are left out rather than sent as `{}` — katcher's fields are nullable for a reason. */
    @Test
    fun `nothing optional is sent empty`() =
        runTest {
            val sent = mutableListOf<HttpRequestData>()

            reporter(accepting(sent)).report(RuntimeException("x"))

            val report = decode(sent.single())
            assertNull(report.context)
            assertNull(report.breadcrumbs)
        }

    /** A report that cannot name its build is refused where it is configured, not where it is sent. */
    @Test
    fun `a blank build identifier is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { config(release = "") }
        assertFailsWith<IllegalArgumentException> { config(appKey = "") }
    }

    private fun accepting(into: MutableList<HttpRequestData>) =
        HttpClient(
            MockEngine { request ->
                into += request
                respond("", HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "text/plain"))
            },
        )

    private fun answering(status: HttpStatusCode) =
        HttpClient(MockEngine { respond("", status, headersOf(HttpHeaders.ContentType, "text/plain")) })

    private fun decode(request: HttpRequestData): CrashReport =
        Json.decodeFromString(CrashReport.serializer(), (request.body as TextContent).text)

    private fun config(
        release: String = "2026.09.02-1a2b3c4",
        appKey: String = "shashki-rider",
    ) = CrashReporterConfig(serverUrl = "https://katcher.example/", appKey = appKey, release = release)

    private fun reporter(client: HttpClient) = CrashReporter(client, config())
}
