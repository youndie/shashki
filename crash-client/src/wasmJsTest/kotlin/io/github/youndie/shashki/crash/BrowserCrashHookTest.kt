@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.crash

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.CreateReportParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two hooks a browser loses an exception through, fired in a real browser.
 *
 * **This is the half of B-10 that was written and never executed.** `installCrashReporting` on
 * `wasmJs` is a `@JsFun` — a string of JavaScript the Kotlin compiler cannot check and no JVM test
 * can reach. It compiled, it was reviewed, and until B-34 put a browser on the build box nothing had
 * ever run it: a typo in `event.reason` or a handler assigned to the wrong object would have shipped
 * as a crash reporter that reports nothing, which is the failure mode a crash reporter has.
 *
 * What is mocked is the transport and only the transport. That a katcher accepts these bytes is
 * `KatcherIngestTest`'s subject and was measured against a running one in B-10; since B-33 the two
 * halves meet at katcher's own `CreateReportParams` rather than at a copy of it.
 */
class BrowserCrashHookTest {
    @Test
    fun `window onerror reaches the ingest`() =
        runTest {
            val landed = CompletableDeferred<HttpRequestData>()
            installCrashReporting(reporter(landed), CoroutineScope(Dispatchers.Default))

            fireWindowError("the map surface was never bound", "at App.kt:42")

            val report = decode(landed.await())
            assertEquals("the map surface was never bound", report.message)
            assertTrue("App.kt:42" in report.stacktrace, "the stack the browser handed over was lost")
            assertEquals(RELEASE, report.release)
        }

    /**
     * **A coroutine that fails after a suspension point never touches `onerror`.** It surfaces as a
     * rejected promise, which in a Compose/Wasm application is most of them — so the listener for it
     * is not a belt-and-braces addition, it is the one that catches the ordinary case.
     */
    @Test
    fun `an unhandled rejection reaches the ingest too`() =
        runTest {
            val landed = CompletableDeferred<HttpRequestData>()
            installCrashReporting(reporter(landed), CoroutineScope(Dispatchers.Default))

            fireUnhandledRejection("the server did not answer", "at HttpRideRepository.kt:31")

            val report = decode(landed.await())
            assertTrue("unhandled rejection" in report.message, "the message does not say what kind of failure it was")
            assertTrue("the server did not answer" in report.message, report.message)
            assertTrue("HttpRideRepository.kt:31" in report.stacktrace, report.stacktrace)
        }

    /**
     * **The test waits on the request rather than on the clock.**
     *
     * The handler's own scope is the application's, not the test's — `installCrashReporting` launches
     * into whatever it is handed — so there is no virtual time to advance and nothing to advance it
     * with. A first version launched into the `TestScope` and called `advanceUntilIdle`, which
     * returned instantly with an empty list: the HTTP call resumes on the browser's event loop and
     * the virtual clock knows nothing about it. Awaiting the request the engine received is the only
     * condition that is actually about the thing under test.
     */
    private fun reporter(landed: CompletableDeferred<HttpRequestData>): CrashReporter =
        CrashReporter(
            HttpClient(
                MockEngine { request ->
                    landed.complete(request)
                    respond("", HttpStatusCode.Created, headersOf())
                },
            ),
            CrashReporterConfig(serverUrl = "https://katcher.example", appKey = "shashki-rider", release = RELEASE),
        )

    private fun decode(request: HttpRequestData): CreateReportParams =
        WIRE.decodeFromString(CreateReportParams.serializer(), (request.body as TextContent).text)

    private companion object {
        const val RELEASE = "2026.09.02-1a2b3c4"

        /** One format for the suite: building one per call is the compiler's own warning. */
        val WIRE = Json { ignoreUnknownKeys = true }
    }
}

/**
 * An `ErrorEvent` at the window, which is how the browser reports a synchronous throw.
 *
 * Dispatched rather than thrown: a test that threw for real would fail the test runner before the
 * handler had a chance, and what is under test is the handler.
 */
@JsFun(
    """(message, stack) => {
        const error = new Error(message);
        error.stack = stack;
        window.dispatchEvent(new ErrorEvent('error', { message: message, error: error }));
    }""",
)
private external fun fireWindowError(
    message: String,
    stack: String,
)

/** The same for a rejected promise, carrying the `reason` the listener reads. */
@JsFun(
    """(message, stack) => {
        const error = new Error(message);
        error.stack = stack;
        const event = new Event('unhandledrejection');
        event.reason = error;
        window.dispatchEvent(event);
    }""",
)
private external fun fireUnhandledRejection(
    message: String,
    stack: String,
)
