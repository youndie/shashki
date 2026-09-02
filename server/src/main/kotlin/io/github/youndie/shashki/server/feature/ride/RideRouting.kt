package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.RideRating
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideTip
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.feature.rating.domain.NotFinishedException
import io.github.youndie.shashki.server.feature.rating.domain.RateRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.oidc.JWT_AUTH_OIDC
import ru.workinprogress.oidc.OidcPrincipal
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
    val rateRide by inject<RateRideUseCase>()
    val settle by inject<SettleRideUseCase>()
    val rides by inject<RideRepository>()
    val index by inject<DriverIndex>()
    val clock by inject<PetichClock>()

    post<Rides> {
        val request = call.receive<RideRequest>()
        // **The one claim this server reads off a token.** Not the rider's identity — `riderId` is
        // still a body field until B-09 — but the address a receipt goes to, which is the one thing
        // a client must not be able to choose for somebody else.
        val email = call.principal<OidcPrincipal>()?.email
        call.respond(HttpStatusCode.Created, requestRide(RequestRideUseCase.Params(request, email)).getOrThrow())
    }

    get<Rides.ById> { route ->
        call.respond(rides.find(route.id) ?: throw RideNotFoundException(route.id))
    }

    // **R8's two, and they are the rider's** (B-44): the same tier as ordering, because rating a
    // driver and giving them money are things somebody does about their own ride. Declared here
    // rather than in their own `Route.` function so the switch that protects them stays one switch.
    post<Rides.Rate> { route ->
        val body = call.receive<RideRating>()
        rateRide(RateRideUseCase.Params(route.id, body.stars)).getOrThrow()
        // **204 and not the rating back.** `Rating` is a domain type and answering with it would put
        // it on the wire, which is the thing `:protocol` exists to prevent — the first version did
        // exactly that and the client got "Serializer for class 'Rating' is not found". There is
        // nothing to show either: the screen knows what it sent.
        call.respond(HttpStatusCode.NoContent)
    }

    post<Rides.Tip> { route ->
        val body = call.receive<RideTip>()
        val ride = rides.find(route.id) ?: throw RideNotFoundException(route.id)
        // Refused before the ride is over, like the rating: a tip for a journey still running is a
        // charge for something nobody has received.
        if (ride.status != RideStatus.COMPLETED) throw NotFinishedException(route.id, ride.status)
        require(body.amountCents > 0) { "a tip of ${body.amountCents} cents is not a tip" }
        settle(
            SettleRideUseCase.Params(route.id, SettlementPayload.Kind.TIP, tipCents = body.amountCents),
        ).getOrThrow()
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
