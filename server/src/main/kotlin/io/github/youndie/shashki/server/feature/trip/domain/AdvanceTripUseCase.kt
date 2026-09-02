package io.github.youndie.shashki.server.feature.trip.domain

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripProgression
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload

/**
 * The driver moves the trip along, one state at a time.
 *
 * **Per-driver ownership, checked here rather than in the route.** `authenticate` proves somebody is
 * a driver; that the ride is *theirs* is a fact about the data, and the skill's own rule puts it in
 * the use case beside the id. Today the id arrives in the body — the same seam every driver route
 * carries until B-09 puts it in the token — so this compares it with the driver the order saga
 * assigned, which is the only copy a client cannot choose.
 *
 * **Reaching `COMPLETED` starts the settlement, and that is the one line this whole item is for.**
 * Before it, `PaymentGateway.capture` was implemented and called by nothing.
 */
public class AdvanceTripUseCase(
    private val trips: TripRepository,
    private val rides: RideRepository,
    private val settle: SettleRideUseCase,
    private val reservations: DriverReservations,
) : UseCase<AdvanceTripUseCase.Params, RideView> {
    override suspend fun invoke(params: Params): Result<RideView> =
        suspendRunCatching {
            val ride = rides.find(params.rideId) ?: throw RideNotFoundException(params.rideId)
            val assigned = ride.driverId ?: throw NotThisDriversRideException(params.rideId, params.driverId)
            if (assigned != params.driverId) throw NotThisDriversRideException(params.rideId, params.driverId)

            // No row yet means the trip has not started, which is exactly `ASSIGNED`. The absence is
            // the state rather than a missing record — see `TripRepository`.
            val from = trips.find(params.rideId)?.status ?: RideStatus.ASSIGNED
            if (!TripProgression.isNext(from, params.to)) throw OutOfOrderTransitionException(from, params.to)

            trips.advance(Trip(params.rideId, assigned, params.to))

            // **The driver is free again, and this is where the ride ends** (B-42). `OfferStep`
            // reserves a candidate and keeps the reservation when the driver accepts — that is what
            // stops a second ride being offered to somebody already carrying a passenger — and
            // nothing gave it back: a driver took exactly one ride per process, and every order
            // after it was refused with "no cars nearby" while the rider was still shown their wait.
            //
            // Before the settlement rather than after it: money can fail and be retried, a driver
            // held hostage by a failed capture cannot.
            if (params.to in TERMINAL) reservations.releaseFor(params.rideId)

            // **The settlement runs after the trip's row is written, not inside the same breath.**
            // If it throws, the trip stays completed and the settlement can be retried; the other
            // order would leave a ride that is not finished and money that has moved.
            if (params.to == RideStatus.COMPLETED) {
                settle(SettleRideUseCase.Params(params.rideId, SettlementPayload.Kind.FARE)).getOrThrow()
            }
            rides.find(params.rideId) ?: throw RideNotFoundException(params.rideId)
        }

    private companion object {
        /** Where a ride stops needing a driver. `CANCELLED` arrives here through `CancelRideUseCase`. */
        val TERMINAL = setOf(RideStatus.COMPLETED, RideStatus.CANCELLED)
    }

    public class Params(
        public val rideId: String,
        public val driverId: String,
        public val to: RideStatus,
    )
}
