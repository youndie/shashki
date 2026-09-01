package io.github.youndie.shashki.server.feature.ride.domain

import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus
import java.util.UUID

/**
 * Ask for a car: start the order saga and run it as far as it goes in one pass.
 *
 * One pass is the whole saga today. Once B-12 makes EXECUTION suspend for a driver, one pass ends at
 * `ActionRequired` with the ride in `MATCHING`, and the driver's answer is a second call to the
 * engine — this use case does not change; what it returns does.
 */
public class RequestRideUseCase(
    private val engine: PetichEngine,
    private val rides: RideRepository,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : UseCase<RideRequest, RideView> {
    override suspend fun invoke(params: RideRequest): Result<RideView> =
        suspendRunCatching {
            val rideId = ids()
            val payload =
                OrderPayload(
                    rideId = rideId,
                    riderId = params.riderId,
                    pickup = params.pickup,
                    dropoff = params.dropoff,
                    rideClass = params.rideClass,
                    paymentMethodId = params.paymentMethodId,
                )
            when (
                val result =
                    engine.process(
                        Petich(id = rideId, type = ORDER_SAGA_TYPE, status = PetichStatus.DRAFT, payload = payload),
                    )
            ) {
                // A refusal, a rollback and a completion all leave a row; the rider reads the row.
                is PetichResult.Success, is PetichResult.ActionRequired, is PetichResult.Error -> Unit

                is PetichResult.SystemFailure -> error("order saga $rideId failed systemically: ${result.details}")
            }
            rides.find(rideId) ?: error("order saga $rideId ran and left no row")
        }
}
