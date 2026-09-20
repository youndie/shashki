package io.github.youndie.shashki.server.feature.receipt.domain

/**
 * Who has already been sent a receipt, so that nobody is sent one twice (B-92).
 *
 * **Written before the mail, not after.** petich advances a saga's position only after a member's
 * body returns, so the pass that sends the receipt can run again — on a crash, and far more often on
 * an optimistic-lock conflict, which `processWithRetry` answers by re-running the whole pass up to
 * `maxProcessAttempts` times. A record written after the send would be written by the pass that did
 * not commit, which is the case it exists for.
 *
 * **At most once, deliberately.** A process dying between the claim and the send loses that receipt.
 * That is the failure this product already accepts — `SendReceiptUseCase` swallows a send failure on
 * the grounds that a settlement rolled back over a mail server would be the tail wagging the dog,
 * and `Settled.RECEIPT` exists so a ride whose receipt never went can be found afterwards. A missing
 * receipt is something this system notices and lives with. A duplicate is not.
 */
public interface ReceiptClaims {
    /**
     * Take [key] for [rideId], or report that somebody already has it.
     *
     * `true` means this caller may send; `false` means a previous attempt already did or is doing so.
     * The key is `ctx.idempotencyKey` at the call site — the same string on every attempt at that
     * member, without the member inventing one.
     */
    public suspend fun claim(
        key: String,
        rideId: String,
    ): Boolean
}
