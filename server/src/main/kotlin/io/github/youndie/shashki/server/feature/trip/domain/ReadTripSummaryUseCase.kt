package io.github.youndie.shashki.server.feature.trip.domain

import io.github.youndie.shashki.protocol.TripSummaryView
import io.github.youndie.shashki.server.billing.Payout
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.settlement.saga.Commission

/**
 * D5: what the trip that just ended paid the driver (B-70).
 *
 * **Read off the payout rows, not recomputed from the fare.** The share is what the settlement wrote
 * down as owed, and `feeCents` is the fare minus that row — so the screen's "service fee" is the
 * money that actually did not reach the driver, and stays right if the commission ever changes
 * between the ride and the reading. `feePercent` is carried only so the line can be *named* the way
 * the kit names it.
 *
 * **Refused for a ride that is not this driver's, and refused as 404** — the same answer `advance`
 * gives, because confirming that somebody else's trip exists is itself an answer.
 */
public class ReadTripSummaryUseCase(
    private val rides: RideRepository,
    private val payouts: PayoutRepository,
    private val commission: Commission,
    /** Where "today" starts, in epoch millis — the route decides the zone, as D6's does. */
    private val startOfToday: () -> Long,
) : UseCase<ReadTripSummaryUseCase.Params, TripSummaryView> {
    override suspend fun invoke(params: Params): Result<TripSummaryView> =
        suspendRunCatching {
            val ride = rides.find(params.rideId) ?: throw NotThisDriversRideException(params.rideId, params.driverId)
            if (ride.driverId != params.driverId) throw NotThisDriversRideException(params.rideId, params.driverId)
            // **A ride the settlement has not paid out is not a summary.** The row is written by the
            // settlement's execution phase; until it exists there is nothing honest to show.
            val owed = payouts.forRide(params.rideId)
            val fare = owed.firstOrNull { it.kind == Payout.FARE } ?: throw NoSummaryYetException(params.rideId)
            val fareCents = ride.chargedCents ?: ride.quote?.amountCents ?: throw NoSummaryYetException(params.rideId)
            val quote = ride.quote ?: throw NoSummaryYetException(params.rideId)

            TripSummaryView(
                rideId = ride.id,
                payoutCents = fare.amountCents,
                fareCents = fareCents,
                feeCents = fareCents - fare.amountCents,
                feePercent = commission.platformPercent,
                tipCents = owed.filter { it.kind == Payout.TIP }.sumOf { it.amountCents },
                currency = fare.currency,
                distanceMetres = quote.distanceMetres,
                durationSeconds = quote.durationSeconds,
                paymentMethodId = ride.paymentMethodId.orEmpty(),
                todayCents = payouts.sumFor(params.driverId, startOfToday()),
            )
        }

    public class Params(
        public val rideId: String,
        public val driverId: String,
    )
}

/** Asked for the summary of a trip the settlement has not paid out yet. A 404: nothing at this address now. */
public class NoSummaryYetException(
    public val rideId: String,
) : RuntimeException("ride $rideId has not been paid out yet, so it has no summary")
