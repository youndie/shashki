package io.github.youndie.shashki.rider.feature.ride.ui

import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.format.money
import io.github.youndie.shashki.ui.screens.CancelPrompt

/**
 * R10's copy, and the reason it is a function of the ride.
 *
 * **The amount is shown before the button.** A confirmation that says "a fee may apply" is a product
 * hiding its own rule; this one exists to show the seam, and the seam is that cancelling before a
 * driver has set off compensates the saga and costs nothing, while cancelling after settles a
 * smaller number. `cancellationFeeCents` is `0` for the first and the fee for the second, and the
 * server is the one that knows which — the client asks rather than multiplying.
 */
internal fun cancelPrompt(ride: RideView?): CancelPrompt {
    val fee = ride?.cancellationFeeCents
    val currency = ride?.quote?.currency ?: "USD"
    return CancelPrompt(
        title = "cancel the ride?",
        message =
            when {
                fee == null || fee == 0L -> "nothing has been charged, and nothing will be."
                else -> "a driver is on the way, so cancelling now costs ${money(fee, currency)}."
            },
        confirmLabel = "cancel the ride",
        dismissLabel = "keep waiting",
    )
}
