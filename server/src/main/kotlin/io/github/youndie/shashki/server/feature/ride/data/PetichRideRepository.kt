package io.github.youndie.shashki.server.feature.ride.data

import io.github.youndie.shashki.protocol.DriverView
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import io.github.youndie.shashki.server.feature.rating.domain.RatingRepository
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.Commission
import io.github.youndie.shashki.server.feature.settlement.saga.Settled
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
    /**
     * What the rider already said about this ride (B-59). Read here rather than on a route of its
     * own: R8 asks for one ride and needs to know whether it has been rated before it draws a form.
     */
    private val ratings: RatingRepository,
    /** Who the driver is, once one is assigned (B-63). */
    private val drivers: DriverRepository,
    private val sagaIndex: SagaIndex? = null,
    private val commission: Commission = Commission.DEFAULT,
) : RideRepository {
    /**
     * Merge the reason into the row's enriched payload (B-58).
     *
     * **After the engine is done with the row, never during.** petich updates optimistically, so a
     * write from inside a step would race the engine's own; both call sites here run when `process`
     * has returned. A losing update is dropped rather than retried: the reason is a sentence on a
     * screen, and a ride that lost the race still shows the status it really has.
     */
    override suspend fun recordRejection(
        rideId: String,
        reason: String,
    ) {
        val row = petiches.findById(rideId) ?: return
        val data = (row.enrichedPayload as? SimpleEnrichedPayload)?.data.orEmpty()
        if (data.containsKey(Enriched.REJECTION)) return
        // **The version is the next one, not the one that was read.** petich's optimistic update
        // matches the row it is *replacing* — the engine hands it a row it has already advanced — so
        // an update that carried the version it had just read matched nothing and returned `false`,
        // silently. That is how this was still null after the reason was being recorded.
        petiches.update(
            row.copy(
                version = row.version + 1,
                enrichedPayload = SimpleEnrichedPayload(data + (Enriched.REJECTION to reason)),
            ),
        )
    }

    override suspend fun find(id: String): RideView? {
        val ride = petiches.findById(id)?.toRideView() ?: return null
        // Only forward. A cancelled order saga stays cancelled even if a stale trip row says the car
        // was arriving, because the saga is the record and the trip is the overlay.
        val trip = trips.find(id)?.takeIf { ride.status == RideStatus.ASSIGNED }
        val current = trip?.let { ride.copy(status = it.status) } ?: ride
        return current.copy(
            driver =
                current.driverId
                    ?.let(drivers::find)
                    ?.let { DriverView(it.name, it.car, it.plate, ratings.averageFor(it.id)) },
            stars = ratings.find(id)?.stars,
            cancellationFeeCents = current.cancellationFee(),
            // **What the settlement took, read off the settlement's own row** (B-44). It lives in a
            // different saga — `<ride>:settlement` — because the ride's row is the order's; reading
            // it here is what lets R8 show a sum rather than a promise.
            chargedCents = charged(id),
        )
    }

    /**
     * The rider's own rides, newest first (B-45).
     *
     * **Whose a ride is comes from the token's address**, which is the one thing about a rider the
     * order saga did not take from a request body — B-26 put it there so a receipt could not be sent
     * to somebody else's inbox, and it is the only identity the store has. With no provider
     * configured there is no address on any row and no principal on any request, and every ride
     * belongs to the one rider the demo has; that is written down here rather than answered with an
     * empty list nobody can explain.
     */
    override suspend fun mine(riderEmail: String?): List<RideView> {
        val index = sagaIndex ?: return emptyList()
        return index
            .rideIds()
            .mapNotNull { id -> petiches.findById(id) }
            .filter { riderEmail == null || (it.payload as? OrderPayload)?.riderEmail == riderEmail }
            .sortedByDescending { (it.payload as? OrderPayload)?.requestedAtEpochMs ?: 0 }
            .mapNotNull { find(it.id) }
    }

    private suspend fun charged(rideId: String): Long? =
        petiches
            .findById(SettleRideUseCase.settlementId(rideId))
            ?.let { (it.enrichedPayload as? SimpleEnrichedPayload)?.data }
            ?.get(Settled.CHARGE_AMOUNT)
            ?.toLongOrNull()

    /**
     * What cancelling this ride would cost right now (B-43).
     *
     * **Computed here so the screen does not have to know the rule.** `Commission` says a quarter of
     * the fare once a driver has set off and nothing before it; the screen shows the number it is
     * given. `null` — not cancellable — is the rider already in the car, and the settlement's own
     * refusal a layer down says the same thing.
     */
    private fun RideView.cancellationFee(): Long? =
        when (status) {
            RideStatus.REQUESTED, RideStatus.MATCHING -> {
                0
            }

            RideStatus.ASSIGNED, RideStatus.ARRIVING, RideStatus.ARRIVED -> {
                quote?.let { it.amountCents * commission.cancellationPercent / PERCENT }
            }

            RideStatus.IN_PROGRESS, RideStatus.COMPLETED, RideStatus.CANCELLED -> {
                null
            }
        }
}

private const val PERCENT = 100L

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
        paymentMethodId = order.paymentMethodId,
        requestedAtEpochMs = order.requestedAtEpochMs,
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
