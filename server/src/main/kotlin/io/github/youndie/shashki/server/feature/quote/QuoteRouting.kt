package io.github.youndie.shashki.server.feature.quote

import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.Quotes
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import io.github.youndie.shashki.server.observability.Observability
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.tracy.agent.withSpan

/**
 * `POST /api/quotes` — one road, priced for every class, with the wait for each.
 *
 * **Auth tier: public, for the same reason `/api/routes` is.** A rider sees what a journey costs
 * before signing in; R4 draws three prices on a screen the application reaches while still
 * anonymous. Nothing here is anybody's data.
 *
 * **One route estimate, three prices.** Asking the estimator per class would be three graph searches
 * for one journey and would let the three rows disagree about the distance — the class changes the
 * coefficient, not the road.
 *
 * No use case: a pure computation with no side effect and no ownership to check goes straight to the
 * ports it reads.
 */
public fun Route.quoteRoutes() {
    val estimator by inject<RouteEstimator>()
    val pricing by inject<Pricing>()
    val pickupEta by inject<PickupEta>()
    val drivers by inject<DriverRepository>()
    val observability by inject<Observability>()

    // `withSpan` on an agent that is not there would be a null check at every call site; this is the
    // same three lines once. Outside a request it is a no-op that still runs the block, which is
    // tracy's own behaviour and the reason a span cannot invent a parent.
    suspend fun <T> span(
        name: String,
        block: suspend () -> T,
    ): T = observability.tracy?.let { withSpan(name, it) { block() } } ?: block()

    post<Quotes> {
        val request = call.receive<RouteRequest>()
        // **Named spans, because a trace with one span called `POST` is the library installed rather
        // than used.** tracy instruments the boundaries and nothing else on purpose — time nobody
        // wrapped shows up as unattributed rather than disappearing — so the two things this route
        // actually spends time on say so themselves: a graph search, and a candidate query plus a
        // second search per class.
        val estimate = span("route.estimate") { estimator.estimate(request.from, request.to) }
        call.respond(
            QuotesView(
                distanceMetres = estimate.distanceMetres,
                durationSeconds = estimate.durationSeconds,
                // **One search for the journey, one more per class that has a candidate.** The road
                // from A to B is the same whichever class drives it, so it is estimated once; the
                // wait is a different road each time — from wherever that class's nearest driver is.
                classes =
                    span("pricing.quote") {
                        RideClass.entries.map { rideClass ->
                            val wait = span("dispatch.pickupEta") { pickupEta.waitFor(request.from, rideClass) }
                            ClassQuote(
                                rideClass = rideClass,
                                quote = pricing.quote(request.from, rideClass, estimate),
                                pickupEtaSeconds = wait?.seconds,
                                // The car of the driver the wait was routed for (B-72) — the record's
                                // own string, and `null` for a driver this server has no record of,
                                // which the tile draws as the wait alone rather than as a guess.
                                car = wait?.let { drivers.find(it.driverId)?.car },
                            )
                        }
                    },
            ),
        )
    }
}
