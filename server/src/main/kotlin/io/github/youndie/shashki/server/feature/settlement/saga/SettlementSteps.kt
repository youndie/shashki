package io.github.youndie.shashki.server.feature.settlement.saga

import io.github.youndie.petich.PetichAnnouncement
import io.github.youndie.petich.PetichAnnouncementContext
import io.github.youndie.petich.PetichCheck
import io.github.youndie.petich.PetichCheckContext
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichMemberContext
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.PetichStepRecord
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.petich.petichDefinition
import io.github.youndie.petich.recorded
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.billing.Payout
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.saga.RideOutboxEvent
import io.github.youndie.shashki.server.observability.Observability
import io.github.youndie.tracy.agent.withSpan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * The announcing half of [SettlementStep], and the difference is the whole of petich B-41.
 *
 * A member here runs when the ride is assigned or the money has moved. It cannot `reject`, `fail`,
 * park the saga or be compensated, because none of those verbs is on its context or its type — and
 * an exception it throws is counted by petich rather than rolled back. The span is wrapped the same
 * way and off the same name, so a trace does not care which half a member belongs to.
 */
public abstract class SettlementAnnouncement : PetichAnnouncement<SettlementPayload> {
    final override suspend fun announce(
        ctx: PetichAnnouncementContext,
        payload: SettlementPayload,
    ) {
        val agent = tracing?.tracy ?: return run(ctx, payload)
        withSpan(SettlementStep.spanName(ctx.stepKey), agent) { run(ctx, payload) }
    }

    protected abstract suspend fun run(
        ctx: PetichAnnouncementContext,
        payload: SettlementPayload,
    )

    /** Set once by the graph, exactly as on [SettlementStep]. */
    public var tracing: Observability? = null
}

/**
 * One step per phase, like the order saga's, and for the same reason: a step exists because it can
 * be undone, so the two are the same list.
 *
 * **`supports` is what lets both sagas share one engine.** The row carries a [SettlementPayload] or
 * an `OrderPayload`; each interceptor answers for the one it knows and the engine skips the rest.
 */

public abstract class SettlementStep : PetichStep<SettlementPayload> {
    /**
     * One span per member, in one place — `OrderStep` carries the argument.
     *
     * `supports` used to sit beside this: the row carried a [SettlementPayload] or an `OrderPayload`
     * and each interceptor answered for the one it knew. The definition's type answers that now, so
     * a member of this saga is never handed the other one's payload.
     */
    final override suspend fun execute(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {
        val agent = tracing?.tracy ?: return run(ctx, payload)
        withSpan(spanName(ctx.stepKey), agent) { run(ctx, payload) }
    }

    protected abstract suspend fun run(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    )

    public var tracing: Observability? = null

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {}

    public companion object {
        /**
         * **Named, so that a test can read it.** The first version built this string inline and
         * shipped a name with the dollar sign still in it to the collector — an unexpanded template,
         * invisible to the compiler and to every test, and found by looking at what actually
         * arrived. A name is a value like any other and is asserted like one.
         *
         * **Keyed by the member rather than by its phase**, which is what the definition model
         * changed: a step carries no phase any more, and the key is the address the saga's own row
         * is written against. It is also stricter — the builder refuses two members under one key,
         * so span names cannot collide by construction rather than by a test noticing.
         */
        public fun spanName(key: String): String = "saga.settlement.$key"
    }
}

/**
 * The same span, for the members that have nothing to undo.
 *
 * Two bases rather than one, because the model's two roles are two types (D2) and a check cannot
 * extend a step. What they share — the span and its name — is the companion above.
 */
public abstract class SettlementCheck : PetichCheck<SettlementPayload> {
    final override suspend fun check(
        ctx: PetichCheckContext,
        payload: SettlementPayload,
    ) {
        val agent = tracing?.tracy ?: return run(ctx, payload)
        withSpan(SettlementStep.spanName(ctx.stepKey), agent) { run(ctx, payload) }
    }

    protected abstract suspend fun run(
        ctx: PetichCheckContext,
        payload: SettlementPayload,
    )

    public var tracing: Observability? = null
}

/** What a member reads out of the payload the saga carries forward. */
internal fun PetichMemberContext.enriched(key: String): String? =
    (petich.enrichedPayload as? SimpleEnrichedPayload)?.data?.get(key)

/**
 * ENRICHMENT: what is owed, and to whom.
 *
 * **A fare is the quote; a fee is a fraction of it**, and the fraction is the one number in this
 * saga that a product person would argue about — so it is a named constant with the argument beside
 * it rather than a literal in an expression. Nothing to undo: arithmetic.
 *
 * The driver's share is the platform's take subtracted, and it is deliberately computed from the
 * **charge** rather than from the quote: on a cancellation the driver is paid a share of the fee,
 * which is smaller and is the point of a fee.
 */
public class ChargeAndPayout(
    private val commission: Commission = Commission.DEFAULT,
) : SettlementCheck() {
    override suspend fun run(
        ctx: PetichCheckContext,
        payload: SettlementPayload,
    ) {
        val charge =
            when (payload.kind) {
                SettlementPayload.Kind.FARE -> payload.quote.amountCents
                SettlementPayload.Kind.FEE -> commission.feeOf(payload.quote.amountCents)
                SettlementPayload.Kind.TIP -> payload.tipCents
            }
        // **The whole tip goes to the driver** (B-44). A platform cut of a tip is a policy, and a
        // demo that invented one would be teaching it; the rider gave the money to a person.
        val payout =
            if (payload.kind == SettlementPayload.Kind.TIP) charge else commission.payoutOf(charge)
        ctx.enrich(
            SimpleEnrichedPayload(
                mapOf(
                    Settled.CHARGE_AMOUNT to charge.toString(),
                    Settled.PAYOUT_AMOUNT to payout.toString(),
                    Settled.CURRENCY to payload.quote.currency,
                ),
            ),
        )
    }
}

/**
 * The two percentages, in one place.
 *
 * **Integer arithmetic on cents, because a fare is added and compared** — the same reason `Quote`
 * counts cents rather than holding a `Double`. Rounding is down, and down is toward the platform
 * rather than toward the driver, which is a choice somebody should be able to find and change.
 */
public data class Commission(
    val platformPercent: Int,
    val cancellationPercent: Int,
) {
    public fun payoutOf(chargeCents: Long): Long = chargeCents * (HUNDRED - platformPercent) / HUNDRED

    public fun feeOf(fareCents: Long): Long = fareCents * cancellationPercent / HUNDRED

    public companion object {
        /** Twenty per cent to the platform, a quarter of the fare if the rider walks away. */
        public val DEFAULT: Commission = Commission(platformPercent = 20, cancellationPercent = 25)
        private const val HUNDRED = 100L
    }
}

/**
 * VALIDATION: is there anything to settle.
 *
 * Rejects rather than compensates, on the order saga's own rule: nothing before this has a side
 * effect, so a refusal is a refusal and not a rollback. A charge of zero is the case that matters —
 * a cancellation fee on a fare small enough to round to nothing is not a payment, it is a row on
 * somebody's statement for no reason.
 */
public class Settleable : SettlementCheck() {
    override suspend fun run(
        ctx: PetichCheckContext,
        payload: SettlementPayload,
    ) {
        val charge = ctx.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull()
        when {
            payload.holdId.isBlank() -> ctx.reject("no hold to settle against")
            charge == null -> ctx.reject("ENRICHMENT left no amount")
            charge <= 0 -> ctx.reject("nothing to charge")
        }
    }
}

/**
 * What the capture did, when what it did was charge a card rather than take a hold.
 *
 * Registered in `sagaJson()` beside the payloads: a record whose class is not registered fails to
 * deserialize, and the member it belongs to then compensates blind.
 */
@Serializable
@SerialName("settlement_charged")
public data class Charged(
    val chargeId: String,
) : PetichStepRecord()

/**
 * AUTHORIZATION: take the money.
 *
 * **This is the step the item is about.** `PaymentGateway.capture` had been implemented since B-11
 * and called by nothing, so every ride that could have finished would have left a hold on the card
 * for ever. Its compensation is a refund and not a release — the money has moved, and pretending
 * otherwise in the mock would teach the wrong shape.
 *
 * **The amount is the charge and not the hold**, which is the whole difference between the two
 * settlements: a fare captures what was held, a cancellation fee captures a quarter of it and the
 * rest is never taken. The first version captured the hold, and the fee test is what said so.
 *
 * The gateway refuses a second capture of the same hold, which is where "captured exactly once"
 * actually lives: a process that dies after the money moves and before the row is written retries,
 * and the retry throws instead of charging twice.
 */

public class CaptureStep(
    private val payments: PaymentGateway,
) : SettlementStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {
        val charge =
            ctx.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull() ?: error("AUTHORIZATION reached with no amount")
        // **A tip has no hold to take, so it is a charge** (B-44): the fare's hold was captured when
        // the trip ended and is gone, and `capture` cannot exceed a hold in this gateway or in a
        // real one. What comes back is the id the refund below needs.
        if (payload.kind == SettlementPayload.Kind.TIP) {
            val id = payments.charge(ctx.idempotencyKey, payload.paymentMethodId, charge, payload.quote.currency)
            // RECORDED AGAINST THIS MEMBER rather than merged into the payload the saga carries
            // forward. `Settled.CHARGE_ID` used to live in the enriched payload, which is a shared
            // map with a different lifetime and a different reader — and the only thing that ever
            // read it was the undo three lines below, asking "did I charge?". A record is scoped to
            // the member that wrote it and typed, so that question is asked where it arises.
            ctx.record(Charged(id.value))
            return
        }
        payments.capture(HoldId(payload.holdId), charge)
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {
        // **Undone the way it was done, and the two kinds do not share a fallback** (#13). `run`
        // charges a tip and captures a hold for everything else; the undo splits at the same line.
        //
        // A tip gives back the charge it left behind, and it no longer has to guess whether it left
        // one.
        //
        // **This is where the cost written down here used to be paid** (B-91). `CHARGE_ID` is
        // recorded only after `charge` returns, so a call that reached the far side and lost its
        // answer left no record — and the note that stood here said as much and accepted it: "it
        // stays taken, because there is no id here with which to refund it, and a rollback that says
        // so is better than one that refunds the wrong money". Both halves of that were true. What
        // was missing was a third option.
        //
        // petich B-48 is the third option: the charge is named before it is made, so the undo can
        // REPLAY the same request under the same key and refund the id that comes back. Answered by
        // the first call if it landed, creating and removing it if it did not. The fallback the note
        // rejected stays rejected — the hold this payload carries is the *fare's*, and refunding it
        // would give the rider back the ride they were happy with.
        if (payload.kind == SettlementPayload.Kind.TIP) {
            // The same amount `run` used, from the same place: the replay has to be the SAME request
            // or the gateway is entitled to treat it as a different one.
            val charge = ctx.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull() ?: return
            payments.refund(
                payments.charge(ctx.idempotencyKey, payload.paymentMethodId, charge, payload.quote.currency),
            )
            return
        }
        // **Unchanged, and for a reason worth stating:** a capture is addressed by the hold id the
        // PAYLOAD carries, not by an id that comes back in an answer. Nothing here depends on a
        // reply arriving, so the lost-answer case this item is about cannot reach it.
        payments.refund(HoldId(payload.holdId))
    }
}

/** EXECUTION: what the driver is owed, written down. Compensation removes the row. */
public class PayoutStep(
    private val payouts: PayoutRepository,
) : SettlementStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {
        val amount = ctx.enriched(Settled.PAYOUT_AMOUNT)?.toLongOrNull() ?: error("EXECUTION reached with no payout")
        val currency = ctx.enriched(Settled.CURRENCY) ?: error("EXECUTION reached with no currency")
        payouts.record(Payout(payload.rideId, payload.driverId, amount, currency, payload.payoutKind()))
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: SettlementPayload,
    ) {
        payouts.remove(payload.rideId, payload.payoutKind())
    }
}

/** The outbox key's tail: one event per settlement, and a tip is a second settlement. */
private fun SettlementPayload.eventSuffix(): String = if (kind == SettlementPayload.Kind.TIP) "tipped" else "settled"

/** A tip is the ride's second payout row; everything else is its first. */
private fun SettlementPayload.payoutKind(): String = if (kind == SettlementPayload.Kind.TIP) Payout.TIP else Payout.FARE

/**
 * POST_PROCESSING: the event, and the receipt.
 *
 * **The event is transactional and the receipt is not, and that difference is deliberate.** The
 * outbox row is written by petich in the same transaction as the saga's state, so it cannot be lost;
 * the mail is sent here and its failure is swallowed, because `SendReceiptUseCase` says in as many
 * words whose decision that is — "a settlement that rolled back because a mail server was down would
 * be the tail wagging the dog". The failure is not silent: it goes in the log and in the saga's own
 * enriched payload, so a ride whose receipt never went can be found afterwards.
 *
 * **The swallowing stays, and its reason has changed** (petich B-41). It used to be the only thing
 * standing between a dead mail relay and a refunded fare: this was a `PetichStep`, and a step that
 * throws is compensated. It is a [PetichAnnouncement] now, so petich will not roll a settlement back
 * over it whatever happens here — that part is the type's job and no longer this member's.
 *
 * What is left is the reason a type cannot take away: **a member that dies cannot write down that it
 * died.** `Settled.RECEIPT` is how a ride whose receipt never went is found afterwards, and the only
 * way to record a failed send is to survive it. So the `getOrElse` below is bookkeeping, not
 * defence, and the event is emitted after it so that both readings are committed together.
 *
 * **A settlement with no address is not a failure either.** The rider's email comes from the token
 * (B-26), and a demo pointed at no provider has no token and therefore no address. That is written
 * down rather than papered over with a fabricated recipient.
 */
public class PublishSettled(
    private val json: Json,
    private val receipts: SendReceiptUseCase,
) : SettlementAnnouncement() {
    override suspend fun run(
        ctx: PetichAnnouncementContext,
        payload: SettlementPayload,
    ) {
        val charge = ctx.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull() ?: error("nothing was charged")
        val payout = ctx.enriched(Settled.PAYOUT_AMOUNT)?.toLongOrNull() ?: error("nothing was paid out")
        // **No receipt for a tip** (B-44): the rider was already sent what the ride cost, and a
        // second mail saying "you were generous" is a mail nobody asked for.
        val sent = if (payload.kind == SettlementPayload.Kind.TIP) false else sendReceipt(payload, charge)

        val event =
            RideSettledEvent(
                rideId = payload.rideId,
                riderId = payload.riderId,
                driverId = payload.driverId,
                kind = payload.kind.name,
                chargedCents = charge,
                payoutCents = payout,
                currency = payload.quote.currency,
            )
        ctx.enrich(SimpleEnrichedPayload(mapOf(Settled.RECEIPT to sent.toString())))
        ctx.emit(
            RideOutboxEvent(
                // **A tip's event is its own row.** The outbox key is the idempotence — one
                // settlement, one event — and a tip is a second settlement about the same
                // ride, so it needs a second key rather than a collision. The first version
                // did not have one, and the failure arrived as a `BatchUpdateException`
                // wearing a saga's clothes: "settlement <ride>:tip failed systemically".
                id = "${payload.rideId}:${payload.eventSuffix()}",
                type = RideSettledEvent.TYPE,
                payload = json.encodeToString(RideSettledEvent.serializer(), event),
            ),
        )
    }

    private suspend fun sendReceipt(
        payload: SettlementPayload,
        charge: Long,
    ): Boolean {
        val to = payload.riderEmail
        if (to.isNullOrBlank()) {
            LOG.info("ride {} settled with no address to send a receipt to", payload.rideId)
            return false
        }
        val receipt =
            Receipt(
                rideId = payload.rideId,
                to = to,
                rideClass = payload.rideClass,
                quote = payload.quote.copy(amountCents = charge),
                pickup = payload.pickup,
                dropoff = payload.dropoff,
            )
        return receipts(receipt).getOrElse {
            LOG.warn("the receipt for ride {} did not go: {}", payload.rideId, it.message)
            false
        }
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(PublishSettled::class.java)
    }
}

/**
 * The settlement saga, in the order it runs.
 *
 * It used to be a list of five interceptors whose order came from five `phase` fields, assembled
 * into the same engine as the order saga and told apart by `supports`. The type does that now, and
 * the order is written here.
 */
public fun settlementPetich(
    payments: PaymentGateway,
    payouts: PayoutRepository,
    receipts: SendReceiptUseCase,
    json: Json,
    tracing: Observability? = null,
    commission: Commission = Commission.DEFAULT,
): PetichDefinition<SettlementPayload> {
    // TWO OF THE FIVE TURNED OUT TO BE CHECKS, and their own comments had said so all along:
    // "Nothing to undo: arithmetic" on the first, and "rejects rather than compensates" on the
    // second. The old model could only say it by overriding `compensate` with an empty body — the
    // same sentence a member that genuinely acted and had nothing to give back would write.
    val chargeAndPayout = ChargeAndPayout(commission).also { it.tracing = tracing }
    val settleable = Settleable().also { it.tracing = tracing }
    val capture = CaptureStep(payments).also { it.tracing = tracing }
    val payout = PayoutStep(payouts).also { it.tracing = tracing }
    val publish = PublishSettled(json, receipts).also { it.tracing = tracing }

    // THE TYPE COMES FROM THE CONSTANT the rest of the code already uses, never spelled by hand.
    return petichDefinition(SETTLEMENT_SAGA_TYPE) {
        enrich("charge-and-payout", chargeAndPayout)
        validate("settleable", settleable)
        step("capture", capture)
        step("payout", payout)
        announce("publish-settled", publish)
    }
}

@Serializable
public data class RideSettledEvent(
    val rideId: String,
    val riderId: String,
    val driverId: String,
    /** `FARE` or `FEE` — the two mechanisms under the one word "cancelled". */
    val kind: String,
    val chargedCents: Long,
    val payoutCents: Long,
    val currency: String,
) {
    public companion object {
        public const val TYPE: String = "ride.settled"
    }
}
