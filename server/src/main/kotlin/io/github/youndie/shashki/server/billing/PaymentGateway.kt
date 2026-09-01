package io.github.youndie.shashki.server.billing

import java.util.concurrent.ConcurrentHashMap

/**
 * The payment provider as the saga sees it: a hold that can be released or captured, and nothing
 * else. The brief's "mock cashier" — the provider is emulated, the integration contract is real.
 *
 * `hold` is the AUTHORIZATION step; `release` is its compensation; `capture` belongs to the
 * settlement saga (research §1.4c) and is here so the contract is whole.
 */
public interface PaymentGateway {
    public fun hold(
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId

    public fun release(hold: HoldId)

    public fun capture(hold: HoldId)

    /** What is currently held, for the one question the saga's tests ask: is anything left. */
    public fun activeHolds(): Collection<Hold>
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

    override fun capture(hold: HoldId) {
        holds.remove(hold) ?: error("capture of unknown or already released hold $hold")
    }

    override fun activeHolds(): Collection<Hold> = holds.values.toList()
}
