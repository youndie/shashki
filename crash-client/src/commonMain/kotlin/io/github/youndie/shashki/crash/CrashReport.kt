package io.github.youndie.shashki.crash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What katcher's ingest accepts, transcribed from its own `CreateReportParams`.
 *
 * **This is a copy of somebody else's contract and that is a defect, not a design.** The portfolio's
 * rule is to share the type rather than duplicate it — a copy protects nobody and goes stale in
 * silence. `ru.workinprogress.katcher:shared` holds the original and publishes jvm, four native
 * desktop targets, three iOS ones and mingw; it has no `wasmJs`, so a browser cannot reach it. The
 * module depends on nothing but kotlinx-serialization, so adding the target is two lines — see
 * research §1.6b1, where it is proposed rather than done, because filing in somebody else's
 * repository is asked about first.
 *
 * Field names are katcher's. `release` is what the item calls the build identifier: katcher's Gradle
 * plugin fills it from `KATCHER_BUILD_UUID` on Android, and in a browser there is no plugin, so it
 * is whatever the build put in [CrashReporterConfig.release].
 */
@Serializable
public data class CrashReport(
    val appKey: String,
    val message: String,
    val stacktrace: String,
    val context: Map<String, String>? = null,
    val breadcrumbs: List<Breadcrumb>? = null,
    val release: String? = null,
    val environment: String? = null,
)

/** One step of what the person did before it broke. katcher's shape, again. */
@Serializable
public data class Breadcrumb(
    val message: String,
    @SerialName("timestamp")
    val timestampMillis: Long,
    val category: String? = null,
)
