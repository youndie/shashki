package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `POST /api/rides`, `GET /api/rides/{id}`.
 *
 * **Auth tier: public, and that is temporary and written down rather than inherited.** `riderId`
 * arrives in the body because B-09 has not put it in a token yet; when it does, the tier becomes
 * "user token + owner check in the use case", the field leaves the request, and the `docs/api`
 * table records the change. A route without a chosen tier has one anyway — this one is chosen.
 */
public fun Route.rideRoutes() {
    val requestRide by inject<RequestRideUseCase>()
    val cancelRide by inject<CancelRideUseCase>()
    val rides by inject<RideRepository>()

    post<Rides> {
        val request = call.receive<RideRequest>()
        call.respond(HttpStatusCode.Created, requestRide(request).getOrThrow())
    }

    get<Rides.ById> { route ->
        call.respond(rides.find(route.id) ?: throw RideNotFoundException(route.id))
    }

    post<Rides.Cancel> { route ->
        call.respond(cancelRide(route.id).getOrThrow())
    }
}
