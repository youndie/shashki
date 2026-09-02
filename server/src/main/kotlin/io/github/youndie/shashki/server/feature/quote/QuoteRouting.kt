package io.github.youndie.shashki.server.feature.quote

import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.Quotes
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `POST /api/quotes` — one road, priced for every class.
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

    post<Quotes> {
        val request = call.receive<RouteRequest>()
        val estimate = estimator.estimate(request.from, request.to)
        call.respond(
            QuotesView(
                distanceMetres = estimate.distanceMetres,
                durationSeconds = estimate.durationSeconds,
                classes = RideClass.entries.map { ClassQuote(it, pricing.quote(request.from, it, estimate)) },
            ),
        )
    }
}
