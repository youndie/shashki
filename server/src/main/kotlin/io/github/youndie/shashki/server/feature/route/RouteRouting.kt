package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.protocol.Routes
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `POST /api/routes` — two points in, a road out.
 *
 * **Auth tier: public, deliberately.** Nothing here is anybody's data: the answer is a fact about
 * the city's streets, identical for every caller, and the clients need it before a rider has signed
 * in (the class picker draws a route while the app is still anonymous). What it *is* is the most
 * expensive endpoint this server has per request, so when there is a gateway in front of it this is
 * the route that gets a rate limit — which is a different mechanism from a token and does not become
 * one by adding `authenticate`.
 *
 * No use case: this is a pure computation with no side effect and no ownership to check, so it goes
 * straight to the port. A `Result`-returning wrapper here would add a layer that only unwraps itself.
 */
public fun Route.routeRoutes() {
    val estimator by inject<RouteEstimator>()

    post<Routes> {
        val request = call.receive<RouteRequest>()
        val estimate = estimator.estimate(request.from, request.to)
        call.respond(
            RouteView(
                geometry = estimate.geometry,
                distanceMetres = estimate.distanceMetres,
                durationSeconds = estimate.durationSeconds,
            ),
        )
    }
}
