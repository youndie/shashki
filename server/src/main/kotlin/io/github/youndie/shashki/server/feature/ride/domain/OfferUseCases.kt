package io.github.youndie.shashki.server.feature.ride.domain

import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.dispatch.OfferBoard
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswer
import io.github.youndie.shashki.server.feature.ride.saga.RiderCancelled
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
 * The rider cancels. While the saga is waiting for a driver this is compensation from the middle:
 * the offer withdrawn, the driver freed, the hold released. After a driver is assigned it is a trip
 * ending early and a settlement — not this saga, not this item (research §1.4c).
 */
public class CancelRideUseCase(
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
    private val rides: RideRepository,
) : UseCase<String, RideView> {
    override suspend fun invoke(params: String): Result<RideView> =
        suspendRunCatching {
            val saga = sagas.findById(params) ?: throw RideNotFoundException(params)
            require(saga.status == PetichStatus.PENDING_SIGNATURE) {
                "ride $params is not waiting for a driver (saga is ${saga.status}); cancelling an assigned ride is the trip's business"
            }
            resume(engine, sagas, params, RiderCancelled())
            rides.find(params) ?: throw RideNotFoundException(params)
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
