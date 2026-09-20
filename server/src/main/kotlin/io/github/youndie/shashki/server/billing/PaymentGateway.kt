package io.github.youndie.shashki.server.billing

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The payment provider as the saga sees it: a hold, and the three things that can happen to it.
 * The brief's "mock cashier" — the provider is emulated, the integration contract is real.
 *
 * `hold` is the order saga's EXECUTION step and `release` is its compensation. `capture` is the
 * settlement saga's EXECUTION step and **`refund` is its compensation** — a saga whose money step
 * cannot be undone is a saga in name only, and the step after the capture is a payout, which can
 * fail. (Both said AUTHORIZATION until petich B-39 moved the acting members out of that phase.)
 *
 * **The two calls that CREATE money movement take a caller-chosen key; the three that undo it take
 * the id this gateway generated.** That asymmetry is not an oversight, it is what a real provider
 * offers (B-91): an idempotency key deduplicates a repeat and cannot be cancelled by. So a
 * compensation whose answer was lost cannot say "cancel whatever is under this key" — it **replays**
 * the same request under the same key, reads the id out of the answer it gets back, and undoes by
 * that. petich B-48 calls this the second form and its README writes it out.
 *
 * **What that costs the caller is a retention window**, and it is this gateway's to state: a key is
 * remembered for [KEY_RETENTION], and the replay only works inside it. Longer than any rollback can
 * take, or the replay is not a replay but a second charge — see the value's own note.
 */
public interface PaymentGateway {
    /**
     * [key] names this hold before it is taken, so that a repeat of the same request is answered
     * with the same hold rather than taking a second one.
     *
     * It is `ctx.idempotencyKey` at every call site in the sagas — deterministic, and identical on
     * the forward pass and inside the compensation, which is the whole reason the replay works.
     */
    public fun hold(
        key: String,
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

    /**
     * Money on top of a ride that is already paid for: a tip (B-44).
     *
     * **Not a larger capture, and the interface is where that is said.** `capture` cannot exceed its
     * hold — a real provider's cannot either — and the hold was the quote. Raising the hold at quote
     * time to leave room for a tip charges every rider for one they may never give, which is the
     * kind of thing that gets a product a headline. So this is what it is: a fresh authorisation and
     * capture in one, with no hold behind it, refundable like any other capture.
     */
    public fun charge(
        key: String,
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId

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

    /**
     * What each key produced, which is the half of a provider a mock usually leaves out.
     *
     * Without it the emulation is *easier* than the real thing in the one place the saga leans on
     * it, and a rollback that replays would take a second hold here while working against a real
     * provider. A mock that is kinder than production is a mock that hides B-91.
     *
     * Never evicted, because the process is the window: nothing here survives a restart anyway, and
     * inventing an expiry would be inventing a number. What a deployed provider gives is
     * [KEY_RETENTION], and the note there is the one a real integration has to satisfy.
     */
    private val byKey = ConcurrentHashMap<String, HoldId>()
    private var next = 0

    override fun hold(
        key: String,
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId {
        require(amountCents > 0) { "a hold of $amountCents cents is not a hold" }
        byKey[key]?.let { return it }
        val id = HoldId("hold-${++next}")
        holds[id] = Hold(id, paymentMethodId, amountCents, currency)
        byKey[key] = id
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

    override fun charge(
        key: String,
        paymentMethodId: String,
        amountCents: Long,
        currency: String,
    ): HoldId {
        require(amountCents > 0) { "a charge of $amountCents cents is not a charge" }
        byKey[key]?.let { return it }
        val id = HoldId("charge-${++next}")
        captured[id] = Hold(id, paymentMethodId, amountCents, currency)
        byKey[key] = id
        return id
    }

    override fun refund(hold: HoldId) {
        captured.remove(hold) ?: error("refund of a hold that was never captured: $hold")
    }

    override fun activeHolds(): Collection<Hold> = holds.values.toList()

    override fun captured(): Collection<Hold> = captured.values.toList()
}

/**
 * How long a provider has to remember an idempotency key for the replay in a compensation to work.
 *
 * **A number the integration has to satisfy, not one this code enforces**, and since B-93 the
 * product it is measured against has both its factors. A rollback used to live inside a single
 * `process` call, because `SuspendedPetichSweeper` had no `stuckAfter` and its re-drive was off;
 * now it can span passes, and petich's bound applies:
 *
 *     keyRetention > maxCompensationAttempts × stuckAfter
 *
 * `maxCompensationAttempts` is petich's default **3** — this application configures only
 * `requireOutbox` — and `STUCK_AFTER` in `Application.kt` is **90 s**, so the bound is four and a
 * half minutes. Twenty-four hours is what the providers this mock stands in for offer, and it clears
 * that by two orders of magnitude.
 *
 * Both numbers are named rather than described so that raising either one is visibly a change to
 * this inequality.
 */
public val KEY_RETENTION: Duration = 24.hours
