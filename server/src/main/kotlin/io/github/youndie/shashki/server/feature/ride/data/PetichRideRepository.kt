package io.github.youndie.shashki.server.feature.ride.data

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SimpleEnrichedPayload

/** The ride, read straight off the saga's row. */
public class PetichRideRepository(
    private val petiches: PetichRepository,
) : RideRepository {
    override suspend fun find(id: String): RideView? = petiches.findById(id)?.toRideView()
}

/**
 * Saga state → what the rider sees. The mapping is the whole of research §1.4c in one function:
 * a completed *order saga* is an `ASSIGNED` ride, not a completed one — the trip has not started.
 */
internal fun Petich.toRideView(): RideView {
    val order = payload as? OrderPayload ?: error("saga $id carries ${payload::class.simpleName}, not an order")
    val data = (enrichedPayload as? SimpleEnrichedPayload)?.data.orEmpty()
    val quote =
        listOf(Enriched.QUOTE_DISTANCE, Enriched.QUOTE_DURATION, Enriched.QUOTE_AMOUNT, Enriched.QUOTE_CURRENCY)
            .map { data[it] }
            .takeIf { it.all { v -> v != null } }
            ?.let { (d, t, a, c) -> Quote(d!!.toInt(), t!!.toInt(), a!!.toLong(), c!!) }
    return RideView(
        id = id,
        status = rideStatus(),
        rideClass = order.rideClass,
        pickup = order.pickup,
        dropoff = order.dropoff,
        quote = quote,
        driverId = data[Enriched.DRIVER_ID].takeIf { status == PetichStatus.COMPLETED },
        cancellationReason = data[Enriched.REJECTION],
    )
}

private fun Petich.rideStatus(): RideStatus =
    when (status) {
        PetichStatus.COMPLETED -> {
            RideStatus.ASSIGNED
        }

        PetichStatus.REJECTED, PetichStatus.FAILED, PetichStatus.COMPENSATING -> {
            RideStatus.CANCELLED
        }

        PetichStatus.PENDING_SIGNATURE -> {
            RideStatus.MATCHING
        }

        PetichStatus.DRAFT, PetichStatus.PROCESSING -> {
            if (currentPhase == PetichPhase.EXECUTION) RideStatus.MATCHING else RideStatus.REQUESTED
        }
    }
