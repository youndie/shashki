package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.DriverTicket
import io.github.youndie.shashki.protocol.EarningsView
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.PayoutDayView
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.dispatch.DriverTickets
import io.github.youndie.shashki.server.feature.auth.driverIdentity
import io.github.youndie.shashki.server.feature.documents.documentRoutes
import io.github.youndie.shashki.server.feature.rating.domain.RatingRepository
import io.github.youndie.shashki.server.feature.ride.domain.AnswerOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.FindOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.OfferNotFoundException
import io.github.youndie.shashki.server.feature.trip.domain.ReadTripSummaryUseCase
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import ru.workinprogress.oidc.JWT_AUTH_OIDC
import ru.workinprogress.petich.PetichClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import io.github.youndie.shashki.protocol.DriverEarnings as DriverEarningsResource
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
    val payouts by inject<PayoutRepository>()
    val ratings by inject<RatingRepository>()
    val clock by inject<PetichClock>()
    val readSummary by inject<ReadTripSummaryUseCase>()

    // **D5, the trip that just ended, from the payout row it wrote** (B-70). Behind the same tier as
    // the transitions that produced it; which driver is asking comes from the token where there is
    // one, and a ride that is not theirs is a 404 rather than a 403.
    get<DriverRides.Summary> { route ->
        val driverId = call.driverIdentity(route.driverId)
        call.respond(readSummary(ReadTripSummaryUseCase.Params(route.rideId, driverId)).getOrThrow())
    }

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

    // **D6's three numbers, from the payout rows alone** (B-46). What a driver is owed is what was
    // written down as owed; a figure recomputed from fares agrees with it until the first refund.
    //
    // **The day and the week are UTC**, and that is a seam rather than a decision: a driver in
    // another timezone sees their day roll at the wrong hour. Fixing it needs the driver's zone,
    // which needs a driver record — the same missing thing that makes the class and the rating on a
    // position frame self-reported.
    get<DriverEarningsResource> { route ->
        val driverId = call.driverIdentity(route.driverId)
        val now = Instant.ofEpochMilli(clock.nowEpochMs()).atZone(ZoneOffset.UTC)
        val startOfToday =
            now
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val startOfWeek =
            now
                .toLocalDate()
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        call.respond(
            EarningsView(
                todayCents = payouts.sumFor(driverId, startOfToday),
                weekCents = payouts.sumFor(driverId, startOfWeek),
                allTimeCents = payouts.sumFor(driverId, 0),
                currency = "USD",
                // **The counts, the rating and the days the kit's D2 and D6 draw** (B-81): fares
                // counted rather than rows, so a tip is money in the sum and not a trip in the count.
                todayTrips = payouts.countFor(driverId, startOfToday),
                weekTrips = payouts.countFor(driverId, startOfWeek),
                allTimeTrips = payouts.countFor(driverId, 0),
                rating = ratings.averageFor(driverId),
                days =
                    payouts
                        .daysFor(
                            driverId,
                        ).map { PayoutDayView(it.dayStartEpochMs, it.trips, it.amountCents, it.currency) },
            ),
        )
    }

    // The kit's D1: three documents, uploaded through this server because a browser cannot sign for
    // the store itself (B-47). Declared here so they share the driver's tier rather than repeating
    // the switch that protects it.
    documentRoutes()

    // The socket's half of the token. It is minted here because here is behind the same
    // `authenticate` block as everything else — the whole point of a ticket is that verification
    // happens once, in the one place this server does it.
    post<DriverTicketsResource> {
        val driverId = call.driverIdentity(null)
        call.respond(DriverTicket(tickets.mint(driverId), (DriverTickets.LIFETIME_MS / MILLIS).toInt()))
    }
}

private const val MILLIS = 1_000L
