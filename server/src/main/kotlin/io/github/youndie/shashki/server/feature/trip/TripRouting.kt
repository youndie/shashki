package io.github.youndie.shashki.server.feature.trip

import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.TripAdvance
import io.github.youndie.shashki.server.feature.auth.driverIdentity
import io.github.youndie.shashki.server.feature.trip.domain.AdvanceTripUseCase
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.oidc.JWT_AUTH_OIDC

/**
 * `POST /api/driver/rides/{rideId}/advance` — the driver moves the trip along.
 *
 * **Auth tier: the driver's token, when a provider is configured** (B-52) — the same switch the
 * other three driver routes carry. Which driver is advancing the trip comes from the token; the body
 * field survives for the provider-less demo and is ignored the moment there is a principal.
 *
 * The ownership check stays where it was and is now the second lock rather than the only one:
 * `AdvanceTripUseCase` compares the identity with the driver the *order saga* assigned, so a signed-in
 * driver still cannot drive somebody else's trip.
 */
public fun Route.tripRoutes(protected: Boolean = false) {
    if (protected) {
        authenticate(JWT_AUTH_OIDC) { tripEndpoints() }
    } else {
        tripEndpoints()
    }
}

private fun Route.tripEndpoints() {
    val advance by inject<AdvanceTripUseCase>()

    post<DriverRides.Advance> { route ->
        val body = call.receive<TripAdvance>()
        call.respond(
            advance(
                AdvanceTripUseCase.Params(route.rideId, call.driverIdentity(body.driverId), body.to),
            ).getOrThrow(),
        )
    }
}
