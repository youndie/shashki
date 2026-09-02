package io.github.youndie.shashki.server.billing

import java.util.concurrent.ConcurrentHashMap

/**
 * The payment provider as the saga sees it: a hold, and the three things that can happen to it.
 * The brief's "mock cashier" — the provider is emulated, the integration contract is real.
 *
 * `hold` is the order saga's AUTHORIZATION step and `release` is its compensation. `capture` is the
 * settlement saga's AUTHORIZATION step (research §1.4c) and **`refund` is its compensation** — a
 * saga whose money step cannot be undone is a saga in name only, and the step after the capture is a
 * payout, which can fail.
 */
public interface PaymentGateway {
    public fun hold(
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId

    public fun release(hold: HoldId)

    /**
     * Take [amountCents] of the hold, which is at most what was held.
     *
     * **The amount is a parameter and the cancellation fee is why.** The first version captured the
     * whole hold, which is right for a fare and charges a rider the entire journey for a car they
     * sent away — the fee test found it, expecting a quarter and getting the lot. Partial capture is
     * what a real provider offers and it is the difference between two settlements that share five
     * phases and differ in one number.
     */
    public fun capture(
        hold: HoldId,
        amountCents: Long,
    )

    /**
     * Undo a capture. **Not the same as [release]**, which lets go of money nobody took: this gives
     * back money that was taken, which in a real provider is a different call, a different fee and a
     * different row on somebody's statement. Saying so in the interface is the point of the mock.
     */
    public fun refund(hold: HoldId)

    /** What is currently held, for the one question the saga's tests ask: is anything left. */
    public fun activeHolds(): Collection<Hold>

    /** What has actually been taken. The other question: was it taken, and taken once. */
    public fun captured(): Collection<Hold>
}

public data class HoldId(
    val value: String,
)

public data class Hold(
    val id: HoldId,
    val paymentMethodId: String,
    val amountCents: Long,
    val currency: String,
)

/** In memory. Restart and every hold is gone — which for a mock is a feature and for a product is B-13's kind of note. */
public class InMemoryPaymentGateway : PaymentGateway {
    private val holds = ConcurrentHashMap<HoldId, Hold>()
    private val captured = ConcurrentHashMap<HoldId, Hold>()
    private var next = 0

    override fun hold(
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId {
        require(amountCents > 0) { "a hold of $amountCents cents is not a hold" }
        val id = HoldId("hold-${++next}")
        holds[id] = Hold(id, paymentMethodId, amountCents, currency)
        return id
    }

    override fun release(hold: HoldId) {
        holds.remove(hold)
    }

    /**
     * **Refusing a second capture is the mock doing the useful half of a provider's job.**
     * A settlement that ran twice — a process that died after the money moved and before the row
     * was written — would otherwise charge twice and nothing would say so. Here it throws, the saga
     * fails loudly, and the test that kills the process between phases can assert on the amount
     * rather than on the absence of a complaint.
     */
    override fun capture(
        hold: HoldId,
        amountCents: Long,
    ) {
        val held = holds[hold] ?: error("capture of unknown, released or already captured hold $hold")
        require(amountCents in 1..held.amountCents) {
            "cannot capture $amountCents of a hold for ${held.amountCents}"
        }
        holds.remove(hold)
        captured[hold] = held.copy(amountCents = amountCents)
    }

    override fun refund(hold: HoldId) {
        captured.remove(hold) ?: error("refund of a hold that was never captured: $hold")
    }

    override fun activeHolds(): Collection<Hold> = holds.values.toList()

    override fun captured(): Collection<Hold> = captured.values.toList()
}
