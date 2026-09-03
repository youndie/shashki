package io.github.youndie.shashki.server.feature.receipt.domain

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.kompotScreen
import io.github.youndie.kompot.standard.text
import io.github.youndie.shashki.protocol.FareBreakdown
import io.github.youndie.shashki.protocol.FareLine
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.money
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching

/**
 * R9·b: the receipt, composed here and drawn there (B-61).
 *
 * **This is the first screen this product sends out of its *own* components**, and the reason it is
 * this screen is that a receipt has no native version worth having: which lines a charge is broken
 * into is a decision about money, and money is the server's. A client that assembled the same card
 * from a DTO would be a second opinion about what a card was charged.
 *
 * **Nothing here computes a total.** The figure is what the settlements moved — the fare charge plus
 * the tip charge, both read from the sagas that made them — and the lines under it are the same
 * numbers named. The rider's application adds nothing up; it draws what it is given, which is the
 * property [B-61](../../../../../../../../docs/backlog/B-61-the-history-row-and-the-receipt.md) asks
 * for by name.
 */
public class ReceiptScreenUseCase(
    private val receipts: ReceiptRepository,
) : UseCase<String, KompotComponent> {
    override suspend fun invoke(params: String): Result<KompotComponent> =
        suspendRunCatching {
            receiptTree(receipts.settled(params) ?: throw NoReceiptException(params))
        }
}

/** Asked for the receipt of a ride nobody has settled. A 404: there is nothing to show yet. */
public class NoReceiptException(
    public val rideId: String,
) : RuntimeException("ride $rideId has not been settled, so it has no receipt")

/**
 * The tree.
 *
 * A heading and the card, which is all a receipt is. **`FareBreakdown` is marked `primary`**, so the
 * kit gives the figure its 54 and caps every line under it at 19 — the composition rule B-17 put in
 * the renderer, exercised here by a server that asks for it rather than by a fixture.
 */
internal fun receiptTree(ride: SettledRide): KompotComponent =
    kompotScreen {
        spacing(SPACING_DP)
        text("receipt", style = TypographyToken(ShashkiTokens.TYPE_PAGE_TITLE), id = "receipt-title")
        text(
            ride.rideId,
            style = TypographyToken(ShashkiTokens.TYPE_META),
            color = ColorToken(ShashkiTokens.COLOR_SUBTLE),
            id = "receipt-ride",
        )
        addComponent(ride.asFareBreakdown())
    }

internal fun SettledRide.asFareBreakdown(): FareBreakdown =
    FareBreakdown(
        id = "receipt-$rideId",
        // **The figure is both charges together**, because that is what the rider paid for this
        // ride: the fare settlement and, when there was one, the tip settlement that followed it.
        amount = money(chargedCents + tipCents, quote.currency),
        caption =
            listOfNotNull(
                rideClass.name.lowercase(),
                quote.distanceMetres.asDistance(),
                quote.durationSeconds.asDuration(),
                "cancelled".takeIf { cancelled },
            ).joinToString(" · "),
        primary = true,
        lines = lines(),
    )

/**
 * What the charge was made of.
 *
 * **A cancelled ride says both numbers.** The fare it would have cost is not what was taken, and a
 * receipt showing only the fee leaves a rider working out a quarter of something they were never
 * told. The percentage itself is deliberately not named here: it is `Commission`'s, and repeating it
 * would be a pricing rule in a second place — the mistake `RideView.cancellationFeeCents` exists to
 * avoid.
 */
private fun SettledRide.lines(): List<FareLine> =
    buildList {
        if (cancelled) {
            add(FareLine("quoted fare", money(quote.amountCents, quote.currency)))
            add(FareLine("cancellation fee", money(chargedCents, quote.currency)))
        } else {
            add(FareLine("fare", money(chargedCents, quote.currency)))
        }
        if (tipCents > 0) {
            add(FareLine("tip", money(tipCents, quote.currency)))
        }
        // The kit's R8 meta names the card and this is the same string: an id and not a number,
        // because this product has no card and printing one would be a fabrication.
        if (paymentMethodId.isNotBlank()) {
            add(FareLine("paid with", paymentMethodId))
        }
    }

private const val SPACING_DP = 16
