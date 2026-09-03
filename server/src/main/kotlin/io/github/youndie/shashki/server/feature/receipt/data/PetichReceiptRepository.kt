package io.github.youndie.shashki.server.feature.receipt.data

import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import io.github.youndie.shashki.server.feature.rating.domain.RatingRepository
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptRepository
import io.github.youndie.shashki.server.feature.receipt.domain.SettledRide
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.Settled
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.SimpleEnrichedPayload

/**
 * The receipt, read off the sagas that charged the card.
 *
 * **Two rows and not one**, because a tip is a settlement of its own: it arrives after the fare has
 * been captured, against the same ride under a different key. Reading only the first would print a
 * receipt that is right about the ride and wrong about the total the rider saw on their statement.
 */
public class PetichReceiptRepository(
    private val petiches: PetichRepository,
    /** Who drove, and what the rider thought of it — the two lines the kit's receipt ends with (B-79). */
    private val drivers: DriverRepository,
    private val ratings: RatingRepository,
) : ReceiptRepository {
    override suspend fun settled(rideId: String): SettledRide? {
        val fare = petiches.findById(SettleRideUseCase.settlementId(rideId)) ?: return null
        val payload = fare.payload as? SettlementPayload ?: return null
        // **A settlement that has not charged anything yet is not a receipt.** The row exists from
        // the moment the saga is drafted; what makes it payable is the amount its AUTHORIZATION
        // phase left behind, and until that is there the honest answer is that there is nothing to
        // show.
        val charged = fare.charge() ?: return null

        return SettledRide(
            rideId = rideId,
            rideClass = payload.rideClass,
            quote = payload.quote,
            chargedCents = charged,
            cancelled = payload.kind == SettlementPayload.Kind.FEE,
            tipCents =
                petiches
                    .findById(SettleRideUseCase.settlementId(rideId, SettlementPayload.Kind.TIP))
                    ?.charge() ?: 0,
            paymentMethodId = payload.paymentMethodId,
            pickup = payload.pickup,
            dropoff = payload.dropoff,
            driver = drivers.find(payload.driverId)?.let { "${it.name} · ${it.car} · ${it.plate}" },
            stars = ratings.find(rideId)?.stars,
        )
    }
}

private fun ru.workinprogress.petich.Petich.charge(): Long? =
    (enrichedPayload as? SimpleEnrichedPayload)?.data?.get(Settled.CHARGE_AMOUNT)?.toLongOrNull()
