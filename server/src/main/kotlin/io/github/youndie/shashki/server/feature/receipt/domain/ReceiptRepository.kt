package io.github.youndie.shashki.server.feature.receipt.domain

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass

/**
 * What a ride's settlement did, which is what a receipt is allowed to say.
 *
 * **Read out of the settlement and not recomputed from the pricing rules.** Re-deriving `base + per
 * km + per minute` here would be a second application of a formula whose constants can change: an
 * old ride's receipt would show today's lines under yesterday's total, and the two would be off by
 * an amount nobody could explain. Every field here was written by the saga that moved the money.
 */
public data class SettledRide(
    val rideId: String,
    val rideClass: RideClass,
    /** What the ride was quoted at — the distance, the duration and the fare that was held. */
    val quote: Quote,
    /** What the card was actually charged for the ride itself. */
    val chargedCents: Long,
    /**
     * Whether that charge was a cancellation fee rather than the fare.
     *
     * The two are the same five phases and the same column, and a receipt that called a quarter of
     * the fare "fare" would be a receipt for a journey that did not happen.
     */
    val cancelled: Boolean,
    /** What the rider gave on top afterwards, or nought. Its own settlement, so its own line. */
    val tipCents: Long,
    val paymentMethodId: String,
)

/**
 * The settlements of one ride.
 *
 * A port because the receipt's shape is worth testing without a database, and because what a
 * settlement leaves behind is a saga's storage detail that the tree should not know.
 */
public interface ReceiptRepository {
    /** `null` for a ride nobody has settled — there is no receipt for a journey still happening. */
    public suspend fun settled(rideId: String): SettledRide?
}
