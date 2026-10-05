package io.github.youndie.shashki.server.feature.promo

import io.github.youndie.shashki.protocol.DegradationReport
import io.github.youndie.shashki.protocol.Degradations
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * How many clients could not draw what this server sent, and what.
 *
 * **The count is the whole feature.** kompot's degradation keeps a screen working when a client meets
 * a component it does not know — correctly, and in complete silence, so a server can go on sending it
 * while half its users see a hole. This is the far end of the sink that exists for exactly that, and
 * B-39's criterion is that the number moves rather than that the binding exists.
 *
 * In memory and per process, like the geo-index: the record of what a build can render is the build,
 * and a durable count would be a table whose rows nobody deletes when a client is upgraded. What it
 * is for is a graph and an alert — [reset] is here so a test can assert a delta rather than a total.
 */
public class DegradationCounter {
    private val byKind = ConcurrentHashMap<String, AtomicLong>()

    /**
     * The same reports, split by what the client drew instead (#43). A second map rather than a
     * longer key, so [count] by kind and type stays the total it always was and the split is a
     * question asked separately — "how many saw a hole" and "how many saw what the server chose" are
     * the numbers a staged rollout is decided on, and kompot 0.38 is what made them different numbers.
     */
    private val byOutcome = ConcurrentHashMap<String, AtomicLong>()

    public fun record(report: DegradationReport) {
        byKind.computeIfAbsent("${report.kind}:${report.componentType}") { AtomicLong() }.incrementAndGet()
        byOutcome
            .computeIfAbsent("${report.kind}:${report.componentType}:${report.outcome}") { AtomicLong() }
            .incrementAndGet()
        LOG.warn(
            "a client could not render {} on {} ({}, drew {})",
            report.componentType,
            report.screen,
            report.kind,
            report.outcome,
        )
    }

    public fun count(
        kind: String,
        componentType: String,
    ): Long = byKind["$kind:$componentType"]?.get() ?: 0

    /** How many of [count] drew [outcome] — one of kompot's three names, or `UNREPORTED`. */
    public fun count(
        kind: String,
        componentType: String,
        outcome: String,
    ): Long = byOutcome["$kind:$componentType:$outcome"]?.get() ?: 0

    public fun total(): Long = byKind.values.sumOf { it.get() }

    private companion object {
        val LOG = LoggerFactory.getLogger(DegradationCounter::class.java)
    }
}

/**
 * `POST /api/screens/degradations`.
 *
 * **Auth tier: public, and chosen.** It is a client saying "I could not draw this", which names a
 * component and a screen and nobody. Putting it behind a token would mean the reports stop exactly
 * when a build is broken enough that signing in does not work.
 *
 * The answer is `202`: the client has said its piece and nothing it does depends on what happens
 * next. A report that failed would otherwise be a second failure on a screen that already has one.
 */
public fun Route.degradationRoutes() {
    val counter by inject<DegradationCounter>()

    post<Degradations> {
        counter.record(call.receive<DegradationReport>())
        call.respond(HttpStatusCode.Accepted)
    }
}
