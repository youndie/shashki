package io.github.youndie.shashki.rider.feature.promo.data

import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.shashki.protocol.DegradationReport
import io.github.youndie.shashki.protocol.Degradations
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The near end of kompot's degradation sink: tell the server what this build could not draw.
 *
 * **The hole is the thing being reported and it is invisible by design.** A component this build has
 * no renderer for draws as a placeholder and the screen keeps working, which is right — and means a
 * server can go on sending it while half its users see nothing there. kompot builds the sink for
 * exactly that and leaves the transport to the application; B-32 recorded that nothing bound it.
 *
 * **Fire and forget, on the application's scope.** The sink is called from composition, where a
 * suspend call has nowhere to go, and a report that failed must not become a second problem on a
 * screen that already has one.
 */
public class ReportingDegradationSink(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val screen: String,
) : KompotDegradationSink {
    override fun onUnknown(
        kind: KompotDegradationKind,
        originalType: String,
        /**
         * Whether the client drew *something* in its place.
         *
         * Reported as it comes rather than filtered here: "drawn as a fallback" and "nothing at all"
         * are two different holes and the second is worse, so the decision about which matters
         * belongs to whoever reads the counter.
         */
        drawnAsFallback: Boolean,
    ) {
        scope.launch {
            runCatching {
                client.post(Degradations()) {
                    // **Set here rather than left to the client's `defaultRequest`.** The
                    // application's client sets it for every call, so this looked redundant — and
                    // any other client, a test's included, then fails to serialise the body and the
                    // `runCatching` below swallows it. A report nobody can see failing is the one
                    // thing this class must not be.
                    contentType(ContentType.Application.Json)
                    setBody(DegradationReport(kind.name, originalType, screen, drawnAsFallback))
                }
            }
        }
    }
}
