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

    public fun record(report: DegradationReport) {
        byKind.computeIfAbsent("${report.kind}:${report.componentType}") { AtomicLong() }.incrementAndGet()
        LOG.warn(
            "a client could not render {} on {} ({})",
            report.componentType,
            report.screen,
            report.kind,
        )
    }

    public fun count(
        kind: String,
        componentType: String,
    ): Long = byKind["$kind:$componentType"]?.get() ?: 0

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
