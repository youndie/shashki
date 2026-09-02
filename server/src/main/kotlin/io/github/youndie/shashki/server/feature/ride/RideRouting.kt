package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.oidc.JWT_AUTH_OIDC
import ru.workinprogress.petich.PetichClock

/**
 * `POST /api/rides`, `GET /api/rides/{id}`.
 *
 * **Auth tier: public, and that is temporary and written down rather than inherited.** `riderId`
 * arrives in the body because B-09 has not put it in a token yet; when it does, the tier becomes
 * "user token + owner check in the use case", the field leaves the request, and the `docs/api`
 * table records the change. A route without a chosen tier has one anyway — this one is chosen.
 */
public fun Route.rideRoutes(protected: Boolean = false) {
    if (protected) {
        authenticate(JWT_AUTH_OIDC) { riderRoutes() }
    } else {
        riderRoutes()
    }
}

/**
 * The rider's own routes.
 *
 * **Auth tier: the rider's token, when a provider is configured.** Asking for a car, reading a ride
 * and cancelling one are all things somebody does to their own ride; `/api/routes`, `/api/quotes`
 * and the promo screen stay public and say so where they are declared, because a price and a road
 * are facts about the city.
 *
 * What a token does *not* yet decide is **which** ride is yours: `RideRequest` still carries a
 * `riderId` as a field. That is B-09's remaining half — the id belongs in the token — and until it
 * moves, authentication here proves somebody signed in and not that the ride is theirs.
 */
private fun Route.riderRoutes() {
    val requestRide by inject<RequestRideUseCase>()
    val cancelRide by inject<CancelRideUseCase>()
    val rides by inject<RideRepository>()
    val index by inject<DriverIndex>()
    val clock by inject<PetichClock>()

    post<Rides> {
        val request = call.receive<RideRequest>()
        call.respond(HttpStatusCode.Created, requestRide(request).getOrThrow())
    }

    get<Rides.ById> { route ->
        call.respond(rides.find(route.id) ?: throw RideNotFoundException(route.id))
    }

    // Where the car is. **The rider's trip screen is the reason this exists**: `RideView` says which
    // driver was assigned and never said where they are, so a rider could watch a status change and
    // not a car (B-28).
    //
    // A silent driver answers `at = null` rather than 404: the ride is real and the assignment
    // stands, the phone is in a tunnel. 404 here would make the screen say "no such ride".
    get<Rides.Driver> { route ->
        val ride = rides.find(route.id) ?: throw RideNotFoundException(route.id)
        val driverId = ride.driverId ?: throw RideNotFoundException("${route.id} has no driver yet")
        val presence = index.whereIs(driverId, clock.nowEpochMs())
        call.respond(AssignedDriverView(driverId = driverId, at = presence?.at))
    }

    post<Rides.Cancel> { route ->
        call.respond(cancelRide(route.id).getOrThrow())
    }
}
