package io.github.youndie.shashki.server.observability

import org.slf4j.LoggerFactory
import ru.workinprogress.tracy.agent.AgentConfig

/**
 * Where the metrics and the traces go, or the fact that they go nowhere.
 *
 * **Absent by default and loud about it**, like every other optional dependency here. A service that
 * silently sends nothing is a service whose dashboards are empty for a reason nobody can name — and
 * the two failures look identical from a graph: "no traffic" and "no agent".
 *
 * **One key per installation and not per service** (metrik's own note), and the service name *is* the
 * identifier — there is no registration, so a typo creates a phantom service rather than an error.
 */
public object ObservabilityConfig {
    public const val SERVICE: String = "shashki-server"

    public const val METRIK_ENDPOINT_VARIABLE: String = "SHASHKI_METRIK_ENDPOINT"
    public const val METRIK_KEY_VARIABLE: String = "SHASHKI_METRIK_KEY"
    public const val TRACY_ENDPOINT_VARIABLE: String = "SHASHKI_TRACY_ENDPOINT"
    public const val TRACY_KEY_VARIABLE: String = "SHASHKI_TRACY_KEY"

    /**
     * What fraction of ordinary requests keeps its trace.
     *
     * **The default is the agent's own 1%**, which is right for a service under load and is why
     * three requests against the stand produced nothing at all until this existed: tail sampling
     * keeps failures and slow calls, and throws away the successful fast ones — so a demo where
     * every request is successful and fast shows an empty collector, and the reader concludes the
     * wiring is broken. A stand sets this to 1. (tracy also honours `X-Tracy-Force` on a single
     * request, which is the per-user version of the same escape.)
     */
    public const val TRACY_SAMPLE_VARIABLE: String = "SHASHKI_TRACY_SAMPLE_RATE"

    /** The agent's own, restated here because this file is where somebody looks for it. */
    private const val DEFAULT_SAMPLE_RATE = 0.01

    private val LOG = LoggerFactory.getLogger(ObservabilityConfig::class.java)

    /** `host:port` for metrik's UDP ingest, or `null`. */
    public fun metrik(env: (String) -> String? = System::getenv): Pair<String, String>? {
        val endpoint = env(METRIK_ENDPOINT_VARIABLE)?.takeIf { it.isNotBlank() }
        val key = env(METRIK_KEY_VARIABLE)?.takeIf { it.isNotBlank() }
        if (endpoint == null || key == null) {
            LOG.warn("no {}/{}: nothing measures this service", METRIK_ENDPOINT_VARIABLE, METRIK_KEY_VARIABLE)
            return null
        }
        return endpoint to key
    }

    /**
     * tracy's agent configuration, or `null`.
     *
     * `instanceId` is the pod's name where there is one. **A record has to be traceable back to a
     * restart** — tracy's own reason — and a constant here would make two lifetimes of the same
     * service indistinguishable in the one place somebody looks after a crash.
     */
    public fun tracy(env: (String) -> String? = System::getenv): AgentConfig? {
        val endpoint = env(TRACY_ENDPOINT_VARIABLE)?.takeIf { it.isNotBlank() }
        val key = env(TRACY_KEY_VARIABLE)?.takeIf { it.isNotBlank() }
        if (endpoint == null || key == null) {
            LOG.warn(
                "no {}/{}: a request's time is unattributed and the saga's phases are invisible",
                TRACY_ENDPOINT_VARIABLE,
                TRACY_KEY_VARIABLE,
            )
            return null
        }
        val sample = env(TRACY_SAMPLE_VARIABLE)?.toDoubleOrNull()
        if (sample != null && sample !in 0.0..1.0) {
            // `AgentConfig` would throw on it, taking the service down over a telemetry setting.
            LOG.warn("{}={} is outside 0..1: using the agent's default", TRACY_SAMPLE_VARIABLE, sample)
        }
        return AgentConfig(
            service = SERVICE,
            apiKey = key,
            endpoint = endpoint,
            instanceId = env("HOSTNAME")?.takeIf { it.isNotBlank() } ?: SERVICE,
            sampleRate = sample?.takeIf { it in 0.0..1.0 } ?: DEFAULT_SAMPLE_RATE,
        )
    }
}

/**
 * The agent, or the fact that there is none.
 *
 * **A wrapper for the reason `Events` is one**: Koin resolves by type and binds only non-nullable
 * ones, so "no tracing configured" cannot be a `single<TracyAgent?>`.
 */
public class Observability(
    public val tracy: ru.workinprogress.tracy.agent.TracyAgent?,
)
