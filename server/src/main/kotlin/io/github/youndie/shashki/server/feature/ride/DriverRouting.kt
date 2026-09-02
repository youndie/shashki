package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverTicket
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.server.dispatch.DriverTickets
import io.github.youndie.shashki.server.feature.auth.driverIdentity
import io.github.youndie.shashki.server.feature.ride.domain.AnswerOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.FindOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.OfferNotFoundException
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.oidc.JWT_AUTH_OIDC
import io.github.youndie.shashki.protocol.DriverTickets as DriverTicketsResource

/**
 * `GET /api/driver/offers/{driverId}`, `POST /api/driver/offers/{rideId}/answer`, and the ticket the
 * position socket is opened with.
 *
 * **Auth tier: the driver's token, when a provider is configured** (B-52). Which driver is asking
 * comes from the token and not from the path or the body — `driverIdentity` says why it replaces
 * rather than compares. The path segment and the body field survive for the provider-less demo,
 * where they are the only source there is, and are ignored the moment a principal exists.
 *
 * Without a provider these are open and say so, which is the same switch the rider's routes carry.
 */
public fun Route.driverRoutes(protected: Boolean = false) {
    if (protected) {
        authenticate(JWT_AUTH_OIDC) { driverEndpoints() }
    } else {
        driverEndpoints()
    }
}

private fun Route.driverEndpoints() {
    val findOffer by inject<FindOfferUseCase>()
    val answerOffer by inject<AnswerOfferUseCase>()
    val tickets by inject<DriverTickets>()

    get<DriverOffers.ForDriver> { route ->
        val driverId = call.driverIdentity(route.driverId)
        call.respond(findOffer.forDriver(driverId) ?: throw OfferNotFoundException(driverId))
    }

    post<DriverOffers.Answer> { route ->
        val answer = call.receive<OfferAnswer>()
        val driverId = call.driverIdentity(answer.driverId)
        call.respond(
            answerOffer(AnswerOfferUseCase.Params(route.rideId, answer.copy(driverId = driverId))).getOrThrow(),
        )
    }

    // The socket's half of the token. It is minted here because here is behind the same
    // `authenticate` block as everything else — the whole point of a ticket is that verification
    // happens once, in the one place this server does it.
    post<DriverTicketsResource> {
        val driverId = call.driverIdentity(null)
        call.respond(DriverTicket(tickets.mint(driverId), (DriverTickets.LIFETIME_MS / MILLIS).toInt()))
    }
}

private const val MILLIS = 1_000L
