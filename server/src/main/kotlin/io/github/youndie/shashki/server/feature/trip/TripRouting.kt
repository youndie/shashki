package io.github.youndie.shashki.server.feature.trip

import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.TripAdvance
import io.github.youndie.shashki.server.feature.trip.domain.AdvanceTripUseCase
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `POST /api/driver/rides/{rideId}/advance` — the driver moves the trip along.
 *
 * **Auth tier: public, temporarily, and chosen** — the same tier and the same seam as the other two
 * driver routes. `driverId` is a body field until B-09 puts it in the driver's token; what stops it
 * being a free-for-all today is that the use case compares it with the driver the *order saga*
 * assigned, which is a value no client supplies. When the token lands the tier becomes "driver
 * token, id taken from it", and the ownership check in `AdvanceTripUseCase` is already where it
 * belongs.
 */
public fun Route.tripRoutes() {
    val advance by inject<AdvanceTripUseCase>()

    post<DriverRides.Advance> { route ->
        val body = call.receive<TripAdvance>()
        call.respond(
            advance(AdvanceTripUseCase.Params(route.rideId, body.driverId, body.to)).getOrThrow(),
        )
    }
}
