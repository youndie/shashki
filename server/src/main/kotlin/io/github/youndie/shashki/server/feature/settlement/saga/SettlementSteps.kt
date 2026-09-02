package io.github.youndie.shashki.server.feature.settlement.saga

import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.billing.Payout
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.saga.RideOutboxEvent
import io.github.youndie.shashki.server.observability.Observability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.workinprogress.petich.InterceptorResult
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPayload
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.SimpleEnrichedPayload
import ru.workinprogress.tracy.agent.withSpan

/**
 * One step per phase, like the order saga's, and for the same reason: a step exists because it can
 * be undone, so the two are the same list.
 *
 * **`supports` is what lets both sagas share one engine.** The row carries a [SettlementPayload] or
 * an `OrderPayload`; each interceptor answers for the one it knows and the engine skips the rest.
 */
public abstract class SettlementStep : PetichInterceptor<SettlementPayload> {
    final override fun supports(payload: PetichPayload): Boolean = payload is SettlementPayload

    /** One span per phase, in one place — `OrderStep` carries the argument. */
    final override suspend fun intercept(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
        val agent = tracing?.tracy ?: return run(petich, payload)
        return withSpan(spanName, agent) { run(petich, payload) }
    }

    /**
     * **Named, so that a test can read it.** The first version built this string inline and shipped
     * a name with the dollar sign still in it to the collector — an unexpanded template, invisible to the
     * compiler and to every test, and found by looking at what actually arrived. A name is a value
     * like any other and is asserted like one.
     */
    public val spanName: String get() = "saga.settlement.$phase.${this::class.simpleName}"

    protected abstract suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult

    public var tracing: Observability? = null

    override suspend fun compensate(
        petich: Petich,
        payload: SettlementPayload,
    ) {}

    protected fun Petich.enriched(key: String): String? = (enrichedPayload as? SimpleEnrichedPayload)?.data?.get(key)
}

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
public class ChargeAndPayoutStep(
    private val commission: Commission = Commission.DEFAULT,
) : SettlementStep() {
    override val phase: PetichPhase = PetichPhase.ENRICHMENT

    override suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
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
        return InterceptorResult.Proceed(
            enrichedPayload =
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
public class SettleableStep : SettlementStep() {
    override val phase: PetichPhase = PetichPhase.VALIDATION

    override suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
        val charge = petich.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull()
        return when {
            payload.holdId.isBlank() -> InterceptorResult.Reject("no hold to settle against")
            charge == null -> InterceptorResult.Reject("ENRICHMENT left no amount")
            charge <= 0 -> InterceptorResult.Reject("nothing to charge")
            else -> InterceptorResult.Proceed()
        }
    }
}

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
    override val phase: PetichPhase = PetichPhase.AUTHORIZATION

    override suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
        val charge =
            petich.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull() ?: error("AUTHORIZATION reached with no amount")
        // **A tip has no hold to take, so it is a charge** (B-44): the fare's hold was captured when
        // the trip ended and is gone, and `capture` cannot exceed a hold in this gateway or in a
        // real one. What comes back is the id the refund below needs.
        if (payload.kind == SettlementPayload.Kind.TIP) {
            val id = payments.charge(payload.paymentMethodId, charge, payload.quote.currency)
            return InterceptorResult.Proceed(
                enrichedPayload = SimpleEnrichedPayload(mapOf(Settled.CHARGE_ID to id.value)),
            )
        }
        payments.capture(HoldId(payload.holdId), charge)
        return InterceptorResult.Proceed()
    }

    override suspend fun compensate(
        petich: Petich,
        payload: SettlementPayload,
    ) {
        // The tip's own charge, or the fare's hold. A compensation that refunded the hold for a tip
        // would give back the fare — the ride the rider was happy with.
        val id = petich.enriched(Settled.CHARGE_ID) ?: payload.holdId
        payments.refund(HoldId(id))
    }
}

/** EXECUTION: what the driver is owed, written down. Compensation removes the row. */
public class PayoutStep(
    private val payouts: PayoutRepository,
) : SettlementStep() {
    override val phase: PetichPhase = PetichPhase.EXECUTION

    override suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
        val amount = petich.enriched(Settled.PAYOUT_AMOUNT)?.toLongOrNull() ?: error("EXECUTION reached with no payout")
        val currency = petich.enriched(Settled.CURRENCY) ?: error("EXECUTION reached with no currency")
        payouts.record(Payout(payload.rideId, payload.driverId, amount, currency, payload.payoutKind()))
        return InterceptorResult.Proceed()
    }

    override suspend fun compensate(
        petich: Petich,
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
 * **A settlement with no address is not a failure either.** The rider's email comes from the token
 * (B-26), and a demo pointed at no provider has no token and therefore no address. That is written
 * down rather than papered over with a fabricated recipient.
 */
public class PublishSettledStep(
    private val json: Json,
    private val receipts: SendReceiptUseCase,
) : SettlementStep() {
    override val phase: PetichPhase = PetichPhase.POST_PROCESSING

    override suspend fun run(
        petich: Petich,
        payload: SettlementPayload,
    ): InterceptorResult {
        val charge = petich.enriched(Settled.CHARGE_AMOUNT)?.toLongOrNull() ?: error("nothing was charged")
        val payout = petich.enriched(Settled.PAYOUT_AMOUNT)?.toLongOrNull() ?: error("nothing was paid out")
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
        return InterceptorResult.Proceed(
            enrichedPayload = SimpleEnrichedPayload(mapOf(Settled.RECEIPT to sent.toString())),
            outboxEvents =
                listOf(
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
        val LOG = LoggerFactory.getLogger(PublishSettledStep::class.java)
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
