package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.server.feature.ride.domain.AnswerOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.FindOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.OfferNotFoundException
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `GET /api/driver/offers/{driverId}`, `POST /api/driver/offers/{rideId}/answer`.
 *
 * **Auth tier: public, temporarily, and chosen.** `driverId` is a path segment and a body field
 * until B-09 puts it in the driver's token; then the tier becomes "driver token + the offer must be
 * this driver's", checked in the use case, and `docs/api` records it.
 */
public fun Route.driverRoutes() {
    val findOffer by inject<FindOfferUseCase>()
    val answerOffer by inject<AnswerOfferUseCase>()

    get<DriverOffers.ForDriver> { route ->
        call.respond(findOffer.forDriver(route.driverId) ?: throw OfferNotFoundException(route.driverId))
    }

    post<DriverOffers.Answer> { route ->
        val answer = call.receive<OfferAnswer>()
        call.respond(answerOffer(AnswerOfferUseCase.Params(route.rideId, answer)).getOrThrow())
    }
}
