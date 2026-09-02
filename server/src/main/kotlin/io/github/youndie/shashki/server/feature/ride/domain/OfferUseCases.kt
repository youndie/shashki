package io.github.youndie.shashki.server.feature.ride.domain

import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.OfferBoard
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswer
import io.github.youndie.shashki.server.feature.ride.saga.RiderCancelled
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import io.github.youndie.shashki.server.feature.trip.domain.Trip
import io.github.youndie.shashki.server.feature.trip.domain.TripRepository
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus

/**
 * A driver's answer resumes the suspended saga. What comes back is the ride as the *rider* sees it,
 * because that is what changed: `ASSIGNED` on accept, still `MATCHING` on decline while the next
 * candidate is asked, `CANCELLED` when the candidates ran out.
 */
public class AnswerOfferUseCase(
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
    private val rides: RideRepository,
) : UseCase<AnswerOfferUseCase.Params, RideView> {
    override suspend fun invoke(params: Params): Result<RideView> =
        suspendRunCatching {
            val outcome =
                when (params.answer.decision) {
                    DriverDecision.ACCEPT -> DriverAnswer.Outcome.ACCEPT
                    DriverDecision.DECLINE -> DriverAnswer.Outcome.DECLINE
                }
            resume(engine, sagas, params.rideId, DriverAnswer(params.answer.driverId, outcome))
            val ride = rides.find(params.rideId) ?: throw RideNotFoundException(params.rideId)
            // **An accept the saga ignored must not come back as 200.** `DriverAnswerStep` already
            // refuses an answer from a driver who is not the one currently offered — it resuspends,
            // which is correct and completely silent: the ride comes back unchanged, and a client
            // that assumed success would show a trip that belongs to somebody else. So the outcome
            // is read off the ride rather than off the fact that nothing threw.
            if (outcome == DriverAnswer.Outcome.ACCEPT && ride.driverId != params.answer.driverId) {
                throw OfferGoneException(params.rideId, params.answer.driverId)
            }
            ride
        }

    public class Params(
        public val rideId: String,
        public val answer: OfferAnswer,
    )
}

/** Fifteen seconds passed with no answer: the same path as a decline, driven by the clock. */
public class ExpireOfferUseCase(
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
) {
    public suspend fun invoke(
        rideId: String,
        driverId: String,
    ) {
        resume(engine, sagas, rideId, DriverAnswer(driverId, DriverAnswer.Outcome.IGNORED))
    }
}

/**
 * The rider cancels — **one word and two mechanisms**, which is research §1.4c's point and, since
 * B-37, something this product can actually show.
 *
 * While the saga is waiting for a driver, cancelling is compensation from the middle: the offer
 * withdrawn, the driver freed, the hold released, nobody charged. After a driver is assigned there
 * is a driver who set off, so the order saga is finished and cannot be rolled back — what happens
 * instead is a **settlement with a fee**, which is the same five phases as a fare and a smaller
 * number. The two paths diverge here and nowhere else.
 *
 * A ride already finished is neither: `CANCELLED` after `COMPLETED` would be a second settlement
 * against a hold that has been captured, and the gateway would refuse it — loudly, and one layer too
 * late to say anything useful.
 */
public class CancelRideUseCase(
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
    private val rides: RideRepository,
    private val trips: TripRepository,
    private val settle: SettleRideUseCase,
    private val reservations: DriverReservations,
) : UseCase<String, RideView> {
    override suspend fun invoke(params: String): Result<RideView> =
        suspendRunCatching {
            val saga = sagas.findById(params) ?: throw RideNotFoundException(params)
            if (saga.status == PetichStatus.PENDING_SIGNATURE) {
                resume(engine, sagas, params, RiderCancelled())
                return@suspendRunCatching rides.find(params) ?: throw RideNotFoundException(params)
            }

            val ride = rides.find(params) ?: throw RideNotFoundException(params)
            require(ride.status in CANCELLABLE) {
                "ride $params is ${ride.status} and cannot be cancelled"
            }
            val driver = ride.driverId ?: throw RideNotFoundException(params)
            trips.advance(Trip(params, driver, RideStatus.CANCELLED))
            // The other end of a ride, and the same rule as `AdvanceTripUseCase`'s: whoever set off
            // for this rider is free again (B-42). The fee is charged either way.
            reservations.releaseFor(params)
            settle(SettleRideUseCase.Params(params, SettlementPayload.Kind.FEE)).getOrThrow()
            rides.find(params) ?: throw RideNotFoundException(params)
        }

    private companion object {
        /** Assigned, or on the way. Once the rider is in the car the fare is the fare. */
        val CANCELLABLE = setOf(RideStatus.ASSIGNED, RideStatus.ARRIVING, RideStatus.ARRIVED)
    }
}

/** What the driver's app polls: the offer on the board, joined with the ride it is for. */
public class FindOfferUseCase(
    private val board: OfferBoard,
    private val rides: RideRepository,
    private val clock: PetichClock,
) {
    public suspend fun forDriver(driverId: String): OfferView? {
        val offer = board.forDriver(driverId) ?: return null
        val ride = rides.find(offer.rideId) ?: return null
        val quote = ride.quote ?: return null
        return OfferView(
            rideId = ride.id,
            rideClass = ride.rideClass,
            quote = quote,
            pickup = ride.pickup,
            dropoff = ride.dropoff,
            expiresAtEpochMs = offer.expiresAtEpochMs,
            // **The driver's client is told what the clock said here.** It counts the difference
            // rather than subtracting its own wall clock from a deadline it did not set; see
            // `OfferView`.
            nowEpochMs = clock.nowEpochMs(),
        )
    }
}

private suspend fun resume(
    engine: PetichEngine,
    sagas: PetichRepository,
    rideId: String,
    payload: ru.workinprogress.petich.ResumePayload,
) {
    val saga = sagas.findById(rideId) ?: throw RideNotFoundException(rideId)
    when (val result = engine.process(saga.copy(resumePayload = payload))) {
        is PetichResult.Success, is PetichResult.ActionRequired, is PetichResult.Error -> Unit
        is PetichResult.SystemFailure -> error("order saga $rideId failed systemically on resume: ${result.details}")
    }
}
