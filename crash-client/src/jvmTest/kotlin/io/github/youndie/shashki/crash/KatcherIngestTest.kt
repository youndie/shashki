package io.github.youndie.shashki.crash

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reporter against a real katcher, which is what turns B-10's "arrives in katcher" from a claim
 * into a measurement.
 *
 * **Skipped unless a katcher is named, and that is stated rather than hidden.** It needs a running
 * server and an app key issued by it, neither of which CI has. The run that produced the numbers in
 * B-10 is written down there; this is what re-runs it.
 *
 * ```bash
 * docker run -d --name katcher -p 127.0.0.1:8099:8080 -v /tmp/katcher-data:/data \
 *   ghcr.io/youndie/katcher:0.6.2
 * curl -H 'X-Auth-Request-User: tester' -H 'X-Auth-Request-Email: t@example.com' \
 *   -X POST -d 'name=shashki-rider&type=COMPOSE_MULTIPLATFORM' http://127.0.0.1:8099/apps
 * # the key is on /apps/1/key
 * SHASHKI_KATCHER_URL=http://127.0.0.1:8099 SHASHKI_KATCHER_KEY=<key> \
 *   ./gradlew :crash-client:jvmTest --tests '*KatcherIngestTest*'
 * ```
 */
class KatcherIngestTest {
    @Test
    fun `a crash reaches a running katcher, and an unknown key does not`() =
        runTest {
            val url = System.getenv(URL_VARIABLE)
            val key = System.getenv(KEY_VARIABLE)
            assumeTrue(
                !url.isNullOrBlank() && !key.isNullOrBlank(),
                "no $URL_VARIABLE / $KEY_VARIABLE: this test needs a running katcher and a key it issued",
            )

            HttpClient(CIO).use { client ->
                val reporter =
                    CrashReporter(
                        client,
                        CrashReporterConfig(serverUrl = url!!, appKey = key!!, release = RELEASE),
                    )

                val delivered =
                    reporter.report(
                        IllegalStateException("no MapSurface in composition"),
                        context = mapOf("screen" to "RiderTripInProgress", "target" to "wasmJs"),
                    )
                assertTrue(delivered, "katcher did not accept the report")

                // The other half of the contract, and the one a wrong key silently produces: katcher
                // answers 401 to a key it does not know, and the reporter reads that as not sent
                // rather than as a failure to be thrown at a crashing application.
                val refused =
                    CrashReporter(
                        client,
                        CrashReporterConfig(serverUrl = url, appKey = "not-a-key", release = RELEASE),
                    ).report(IllegalStateException("x"))
                assertFalse(refused, "katcher accepted a report signed with an unknown key")
            }
        }

    private companion object {
        const val URL_VARIABLE = "SHASHKI_KATCHER_URL"
        const val KEY_VARIABLE = "SHASHKI_KATCHER_KEY"

        /** What a browser build would put in `release`, since there is no Gradle plugin filling it. */
        const val RELEASE = "2026.09.02-b10"
    }
}
