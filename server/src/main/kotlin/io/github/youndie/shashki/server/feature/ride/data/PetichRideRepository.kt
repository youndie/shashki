package io.github.youndie.shashki.server.feature.ride.data

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.trip.domain.TripRepository
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SimpleEnrichedPayload

/**
 * The ride, read off the saga's row — and, once a driver has started driving, off the trip's.
 *
 * **The trip is an overlay and not a second source of truth.** Everything about the ride is the
 * order saga's: who, where, what it costs, which driver. What the trip row adds is one field, the
 * status, and only after `ASSIGNED` — which is precisely research §1.4c's "a stretch of no saga".
 * A ride with no trip row is a ride nobody has started driving, and that is the honest reading of an
 * absent row rather than a missing record (B-37).
 */
public class PetichRideRepository(
    private val petiches: PetichRepository,
    private val trips: TripRepository,
) : RideRepository {
    override suspend fun find(id: String): RideView? {
        val ride = petiches.findById(id)?.toRideView() ?: return null
        // Only forward. A cancelled order saga stays cancelled even if a stale trip row says the car
        // was arriving, because the saga is the record and the trip is the overlay.
        val trip = trips.find(id)?.takeIf { ride.status == RideStatus.ASSIGNED } ?: return ride
        return ride.copy(status = trip.status)
    }
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
