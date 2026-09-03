package io.github.youndie.shashki.rider.feature.receipt.domain

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.shashki.rider.UseCase
import io.github.youndie.shashki.rider.suspendRunCatching

/**
 * R9·b: what a ride cost, as the server composed it (B-61).
 *
 * **The second screen this client asks for rather than draws**, and the first made of this product's
 * own components. The promo screen proves a client can render a tree it has never seen; this one
 * proves the tree can be about something — a card charge, whose breakdown is a decision that belongs
 * to whoever moved the money.
 */
public interface ReceiptRepository {
    public suspend fun receipt(rideId: String): KompotComponent
}

/**
 * Fetch it.
 *
 * The failure here means "no receipt", which is a state this screen has and the promo's has too:
 * a ride still running has none, and the server answers 404 rather than an empty card.
 */
public class LoadReceiptUseCase(
    private val receipts: ReceiptRepository,
) : UseCase<String, KompotComponent> {
    override suspend fun invoke(params: String): Result<KompotComponent> =
        suspendRunCatching { receipts.receipt(params) }
}
