package io.github.youndie.shashki.crash

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.report.Breadcrumb
import ru.workinprogress.feature.report.CreateReportParams

/** Where katcher is and who we are to it. */
public data class CrashReporterConfig(
    val serverUrl: String,
    val appKey: String,
    /**
     * The build identifier, which is B-10's acceptance criterion.
     *
     * On Android katcher's Gradle plugin writes `KATCHER_BUILD_UUID` into `BuildConfig` and the
     * client reads it. There is no plugin here, so this is the one field the application must supply
     * and the one the reporter refuses to invent: a report that reached the server without saying
     * which build produced it is a report nobody can act on.
     */
    val release: String,
    val environment: String = "production",
) {
    init {
        require(release.isNotBlank()) { "a crash report needs the build it came from; release is blank" }
        require(appKey.isNotBlank()) { "katcher answers 401 to an unknown appKey; this one is blank" }
    }
}

/**
 * Crashes from the browser to katcher's ingest, `POST {serverUrl}/api/reports`.
 *
 * **A request rather than a library change**, which is the decision B-10 records: katcher's own
 * client publishes every target except a browser one, and adding `wasmJs` to it would be changing a
 * library so that one consumer need not write a POST. What that costs is stated here rather than
 * discovered later — the Android client's offline persistence and its build-uuid plumbing are not
 * here, so a report that fails to send is lost and the build identifier is supplied by hand.
 *
 * The ingest is deliberately public: katcher mounts `route("api")` outside its
 * `authenticate(HEADER_USER_AUTH)` block, because an application that has just crashed cannot be
 * asked to sign in. The `appKey` is what identifies it, and an unknown one is answered 401.
 */
public class CrashReporter(
    private val client: HttpClient,
    private val config: CrashReporterConfig,
    private val json: Json = Json { encodeDefaults = false },
) {
    /**
     * Send one report. Returns whether katcher took it.
     *
     * **It does not throw, and that is deliberate**: this is called from an uncaught-exception
     * handler, and a reporter that threw would replace the crash being reported with its own.
     */
    public suspend fun report(
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
        breadcrumbs: List<Breadcrumb> = emptyList(),
    ): Boolean =
        send(
            CreateReportParams(
                appKey = config.appKey,
                message = throwable.message ?: throwable::class.simpleName ?: "unknown",
                stacktrace = throwable.stackTraceToString(),
                context = context.ifEmpty { null },
                breadcrumbs = breadcrumbs.ifEmpty { null },
                release = config.release,
                environment = config.environment,
            ),
        )

    /** The same, for a browser error that never was a `Throwable` — `window.onerror` gives strings. */
    public suspend fun report(
        message: String,
        stacktrace: String,
        context: Map<String, String> = emptyMap(),
    ): Boolean =
        send(
            CreateReportParams(
                appKey = config.appKey,
                message = message,
                stacktrace = stacktrace,
                context = context.ifEmpty { null },
                release = config.release,
                environment = config.environment,
            ),
        )

    private suspend fun send(report: CreateReportParams): Boolean =
        try {
            val response: HttpResponse =
                client.post(config.serverUrl.trimEnd('/') + INGEST) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(CreateReportParams.serializer(), report))
                }
            // 202, not 200: katcher queues the report and answers Accepted. Treating only 200 as
            // success would drop every report while looking like it was sending them.
            response.status == HttpStatusCode.Accepted
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            false
        }

    private companion object {
        const val INGEST = "/api/reports"
    }
}
