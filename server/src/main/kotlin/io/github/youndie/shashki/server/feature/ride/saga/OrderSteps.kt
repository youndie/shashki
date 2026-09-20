package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.petich.OutboxEvent
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichCheck
import io.github.youndie.petich.PetichCheckContext
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichMemberContext
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.petich.petich
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.Offer
import io.github.youndie.shashki.server.dispatch.OfferBoard
import io.github.youndie.shashki.server.observability.Observability
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.ServiceArea
import io.github.youndie.tracy.agent.withSpan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One step per phase, and each is one class because the reason a step exists is the reason it can
 * be undone. The order is petich's; the priorities are all 0 because there is one step per phase.
 */
public abstract class OrderStep : PetichStep<OrderPayload> {
    /**
     * Every member is a span, and it is one line here rather than one per member.
     *
     * **B-39's criterion is that a saga's phases are visible in a trace**, and the natural way to get
     * there — wrapping each body — is ten call sites that a new member forgets. `execute` is final
     * and delegates to [run], so a member written tomorrow is traced tomorrow. Outside a request
     * there is no context and `withSpan` is a no-op that still runs the block, which is exactly what a
     * saga resumed by the sweeper should be: unattributed rather than invented.
     *
     * `supports` used to sit here too: the row carried an [OrderPayload] or a settlement's, and each
     * interceptor answered for the one it knew. The definition's type answers that now.
     */
    final override suspend fun execute(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val agent = tracing?.tracy ?: return run(ctx, payload)
        withSpan(spanName(ctx.stepKey), agent) { run(ctx, payload) }
    }

    protected abstract suspend fun run(
        ctx: PetichStepContext,
        payload: OrderPayload,
    )

    /**
     * The agent, set once by the graph.
     *
     * **A mutable property and not a constructor parameter**, which is the ugly half of this and is
     * deliberate: the members are built where the definition is and threading an agent through
     * ten constructors — six of which do not want one — is how a cross-cutting concern becomes a
     * parameter everybody copies. It is written once, before the engine exists.
     */
    public var tracing: Observability? = null

    /** Members with nothing to undo say so by leaving this alone. */
    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {}

    public companion object {
        /**
         * **Named, so that a test can read it.** The first version built this string inline and
         * shipped a name with the dollar sign still in it to the collector — an unexpanded template,
         * invisible to the compiler and to every test, and found by looking at what actually
         * arrived. A name is a value like any other and is asserted like one.
         *
         * **Keyed by the member rather than by its phase.** A member carries no phase now, and the
         * key is stricter anyway: the builder refuses two members under one key, so two spans cannot
         * share a name by construction rather than by a test noticing.
         */
        public fun spanName(key: String): String = "saga.order.$key"
    }
}

/** The same span, for the members that have nothing to undo and so are checks rather than steps. */
public abstract class OrderCheck : PetichCheck<OrderPayload> {
    final override suspend fun check(
        ctx: PetichCheckContext,
        payload: OrderPayload,
    ) {
        val agent = tracing?.tracy ?: return run(ctx, payload)
        withSpan(OrderStep.spanName(ctx.stepKey), agent) { run(ctx, payload) }
    }

    protected abstract suspend fun run(
        ctx: PetichCheckContext,
        payload: OrderPayload,
    )

    public var tracing: Observability? = null
}

internal fun PetichMemberContext.enriched(key: String): String? =
    (petich.enrichedPayload as? SimpleEnrichedPayload)?.data?.get(key)

internal fun PetichMemberContext.quote(): Quote? {
    val distance = enriched(Enriched.QUOTE_DISTANCE)?.toIntOrNull() ?: return null
    val duration = enriched(Enriched.QUOTE_DURATION)?.toIntOrNull() ?: return null
    val amount = enriched(Enriched.QUOTE_AMOUNT)?.toLongOrNull() ?: return null
    val currency = enriched(Enriched.QUOTE_CURRENCY) ?: return null
    return Quote(distance, duration, amount, currency)
}

/** ENRICHMENT: the route and what it costs. Nothing to undo — a quote is a number. */
public class QuoteStep(
    private val routes: RouteEstimator,
    private val pricing: Pricing,
) : OrderCheck() {
    override suspend fun run(
        ctx: PetichCheckContext,
        payload: OrderPayload,
    ) {
        val estimate = routes.estimate(payload.pickup, payload.dropoff)
        val quote = pricing.quote(payload.pickup, payload.rideClass, estimate)
        ctx.enrich(
            SimpleEnrichedPayload(
                mapOf(
                    Enriched.QUOTE_DISTANCE to quote.distanceMetres.toString(),
                    Enriched.QUOTE_DURATION to quote.durationSeconds.toString(),
                    Enriched.QUOTE_AMOUNT to quote.amountCents.toString(),
                    Enriched.QUOTE_CURRENCY to quote.currency,
                ),
            ),
        )
    }
}

/**
 * VALIDATION: both ends inside the service area. Rejects rather than compensates — nothing before
 * it has side effects, so a rejection is a refusal, not a rollback.
 *
 * The area is a bounding box around the demo city, and a hypothesis until B-06 produces the
 * extract it should be read from. It reaches the airport, because the kit's fixtures go there.
 */
public class ServiceAreaStep(
    /**
     * Asked of the estimator each time rather than captured (B-57): the area is the graph's, and the
     * graph is opened lazily so that a module can be built without one.
     */
    private val area: () -> ServiceArea,
) : OrderCheck() {
    override suspend fun run(
        ctx: PetichCheckContext,
        payload: OrderPayload,
    ) {
        when {
            payload.pickup !in area() -> ctx.reject("pickup is outside the service area")
            payload.dropoff !in area() -> ctx.reject("dropoff is outside the service area")
        }
    }
}

/**
 * AUTHORIZATION: a hold for the quoted amount. **Compensation releases it** — this is the step the
 * whole demo is about: die after this and before the driver, and the hold must not survive.
 */
public class HoldPaymentStep(
    private val payments: PaymentGateway,
) : OrderStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val quote = ctx.quote() ?: return ctx.reject("no quote to hold against")
        val hold = payments.hold(payload.paymentMethodId, quote.amountCents, quote.currency)
        // ENRICHED AND NOT RECORDED, deliberately. A record is evidence for this member's own undo;
        // the hold is read by `SettleRideUseCase` and by the ride's repository long after this saga
        // finished, which is what the payload carried forward is for (petich D4). The undo below
        // reads it back the same way anybody else does.
        ctx.enrich(SimpleEnrichedPayload(mapOf(Enriched.HOLD_ID to hold.value)))
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        ctx.enriched(Enriched.HOLD_ID)?.let { payments.release(HoldId(it)) }
    }
}

/**
 * EXECUTION, first half: ask the nearest candidate and **stop**.
 *
 * Reserves the driver, posts the offer to the board with its deadline, and returns `Suspend` — the
 * saga is parked in the database holding neither a thread nor a connection (research §1.4a: a step
 * that waits is correct until the first driver ignores an offer). The answer arrives as a resume
 * payload and is handled by [DriverAnswerStep], which petich runs next because a suspended step is
 * not re-run on resume.
 *
 * **Two deadlines, deliberately different.** [OFFER_SECONDS] is one driver's — after it, the
 * application resumes the saga with `IGNORED` and the cascade moves on. The `ttl` given to petich
 * is the whole matching budget: if *nobody* answers *anything* for that long, the sweeper rolls the
 * saga back — the hold released, the driver freed — which is the kit's "no cars nearby" after 90 s.
 * petich's expiry is a rollback, not a cascade; the cascade is ours.
 */
public class OfferStep(
    private val candidates: CandidateSource,
    private val reservations: DriverReservations,
    private val board: OfferBoard,
    private val clock: PetichClock,
    private val timeouts: OfferTimeouts,
) : OrderStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val nearby = candidates.candidates(payload.pickup, payload.rideClass)
        val first =
            nearby.firstOrNull { reservations.reserve(it.driverId, payload.rideId) }
                ?: return ctx.fail(NO_CARS)
        offer(ctx, payload, first.driverId, attempt = 0, again = false, nearby = nearby.size)
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        withdraw(ctx, payload)
    }

    internal fun offer(
        ctx: PetichStepContext,
        payload: OrderPayload,
        driverId: String,
        attempt: Int,
        /**
         * Whether the next answer belongs to the member that is asking (B-37).
         *
         * The first offer is made by [OfferStep] and answered by [DriverAnswerStep], so it suspends
         * and the saga moves on by one. Every offer after it is made *from* `DriverAnswerStep`,
         * which has to keep the next answer for itself — the member is the cascade.
         */
        again: Boolean,
        /** How many the index had when this cascade started — the number R5 shows (B-73). */
        nearby: Int,
    ) {
        val expiresAt = clock.nowEpochMs() + OFFER_SECONDS * MILLIS
        board.post(Offer(payload.rideId, driverId, expiresAt))
        timeouts.schedule(payload.rideId, driverId, OFFER_SECONDS)
        val enriched =
            SimpleEnrichedPayload(
                mapOf(
                    Enriched.OFFER_DRIVER to driverId,
                    Enriched.OFFER_ATTEMPT to attempt.toString(),
                    Enriched.OFFER_EXPIRES_AT to expiresAt.toString(),
                    Enriched.OFFER_CANDIDATES to nearby.toString(),
                ),
            )
        ctx.enrich(enriched)
        if (again) {
            ctx.resuspendFor(ACTION_DRIVER_ANSWER, ttl = MATCHING_BUDGET)
        } else {
            ctx.suspendFor(ACTION_DRIVER_ANSWER, ttl = MATCHING_BUDGET)
        }
    }

    /** The offer is over and the driver is free again: decline, ignore, cancel, rollback. */
    internal fun withdraw(
        ctx: PetichMemberContext,
        payload: OrderPayload,
    ) {
        withdrawKeepingReservation(payload)
        ctx.enriched(Enriched.OFFER_DRIVER)?.let { reservations.release(it, payload.rideId) }
    }

    /** The offer is over because the driver took it: off the board, timer cancelled, reservation kept. */
    internal fun withdrawKeepingReservation(payload: OrderPayload) {
        board.withdraw(payload.rideId)
        timeouts.cancel(payload.rideId)
    }

    public companion object {
        /** The kit's OfferCard: fifteen seconds. */
        public const val OFFER_SECONDS: Long = 15

        /** The kit's R5: ninety seconds of asking before "no cars nearby". */
        public val MATCHING_BUDGET: Duration = 90.seconds
        internal const val NO_CARS: String = "no cars nearby"
        private const val MILLIS: Long = 1_000
    }
}

/**
 * EXECUTION, second half: what the driver said.
 *
 * Runs on resume, after [OfferStep] suspended. `ACCEPT` proceeds with the driver final. `DECLINE`
 * and `IGNORED` release that driver, ask the next candidate and `Resuspend` — which keeps the saga
 * at *this* step, so the next answer lands here too. No candidates left is `Compensate`: the hold
 * goes back, the rider sees "no cars nearby". A rider's cancellation is the same `Compensate` with
 * a different reason — compensation from the middle of the saga, D5's whole point.
 */
public class DriverAnswerStep(
    private val candidates: CandidateSource,
    private val reservations: DriverReservations,
    private val offers: OfferStep,
) : OrderStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val offered = ctx.enriched(Enriched.OFFER_DRIVER) ?: return ctx.fail("resumed with no offer out")
        when (val answer = ctx.petich.resumePayload) {
            is RiderCancelled -> {
                offers.withdraw(ctx, payload)
                ctx.fail(answer.reason)
            }

            is DriverAnswer -> {
                if (answer.driverId != offered) {
                    // Somebody else's answer: nothing changed, and the driver who WAS asked still
                    // owes one — so the next answer has to come back here (B-37).
                    return ctx.resuspendFor(ACTION_DRIVER_ANSWER, ttl = OfferStep.MATCHING_BUDGET)
                }
                when (answer.outcome) {
                    DriverAnswer.Outcome.ACCEPT -> {
                        offers.withdrawKeepingReservation(payload)
                        ctx.enrich(SimpleEnrichedPayload(mapOf(Enriched.DRIVER_ID to offered)))
                    }

                    DriverAnswer.Outcome.DECLINE, DriverAnswer.Outcome.IGNORED -> {
                        offers.withdraw(ctx, payload)
                        val attempt = (ctx.enriched(Enriched.OFFER_ATTEMPT)?.toIntOrNull() ?: 0) + 1
                        val nearby = candidates.candidates(payload.pickup, payload.rideClass)
                        val next =
                            nearby
                                .drop(attempt)
                                .firstOrNull { reservations.reserve(it.driverId, payload.rideId) }
                                ?: return ctx.fail(OfferStep.NO_CARS)
                        // The count the rider was first told, kept: the index answers the question
                        // afresh each attempt, and a number that shrank while they watched would
                        // read as cars leaving rather than as the cascade moving down its list.
                        val told = ctx.enriched(Enriched.OFFER_CANDIDATES)?.toIntOrNull() ?: nearby.size
                        offers.offer(ctx, payload, next.driverId, attempt, again = true, nearby = told)
                    }
                }
            }

            // Resumed by something that is not an answer — a retried request, say. Nothing changed;
            // keep waiting for the driver who was asked.
            else -> {
                ctx.resuspendFor(ACTION_DRIVER_ANSWER, ttl = OfferStep.MATCHING_BUDGET)
            }
        }
    }

    override suspend fun compensate(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        ctx.enriched(Enriched.DRIVER_ID)?.let { reservations.release(it, payload.rideId) }
    }
}

/**
 * POST_PROCESSING: the intent to tell the world, written in the same transaction as the saga's own
 * state. petich persists the event through `OutboxAwarePetichRepository`; delivering it is the relay
 * worker's job, and it can only be lost if `requireOutbox` is off — which it is not.
 */
public class PublishAssignedStep(
    private val json: Json,
) : OrderStep() {
    override suspend fun run(
        ctx: PetichStepContext,
        payload: OrderPayload,
    ) {
        val event =
            RideAssignedEvent(
                rideId = payload.rideId,
                riderId = payload.riderId,
                driverId = ctx.enriched(Enriched.DRIVER_ID) ?: error("the announcement was reached with no driver"),
                quote = ctx.quote() ?: error("the announcement was reached with no quote"),
            )
        ctx.emit(
            RideOutboxEvent(
                id = "${payload.rideId}:assigned",
                type = RideAssignedEvent.TYPE,
                payload = json.encodeToString(RideAssignedEvent.serializer(), event),
            ),
        )
    }
}

/**
 * The order saga, in the order it runs.
 *
 * **The two EXECUTION members were `priority = 10` and `priority = 0`.** That pair is the clearest
 * thing the definition model buys here: the order of the offer and the answer to it was two numbers
 * in two files, read by sorting them in your head, and it is now two adjacent lines.
 */
public fun orderPetich(
    routes: RouteEstimator,
    pricing: Pricing,
    area: () -> ServiceArea,
    payments: PaymentGateway,
    offers: OfferStep,
    candidates: CandidateSource,
    reservations: DriverReservations,
    json: Json,
    tracing: Observability? = null,
): PetichDefinition<OrderPayload> {
    val quote = QuoteStep(routes, pricing).also { it.tracing = tracing }
    val serviceArea = ServiceAreaStep(area).also { it.tracing = tracing }
    val hold = HoldPaymentStep(payments).also { it.tracing = tracing }
    val answer = DriverAnswerStep(candidates, reservations, offers).also { it.tracing = tracing }
    val publish = PublishAssignedStep(json).also { it.tracing = tracing }
    offers.tracing = tracing

    // THE TYPE COMES FROM THE CONSTANT the rest of the code already uses, never spelled by hand.
    return petich(ORDER_SAGA_TYPE) {
        // Two of the six turned out to be checks, and their own comments had said so: "Nothing to
        // undo — a quote is a number", and "rejects rather than compensates".
        enrich("quote", quote)
        validate("service-area", serviceArea)
        authorize("hold-payment", hold)
        step("offer", offers)
        step("driver-answer", answer)
        announce("publish-assigned", publish)
    }
}

@Serializable
public data class RideAssignedEvent(
    val rideId: String,
    val riderId: String,
    val driverId: String,
    val quote: Quote,
) {
    public companion object {
        public const val TYPE: String = "ride.assigned"
    }
}

public data class RideOutboxEvent(
    override val id: String,
    override val type: String,
    override val payload: String,
) : OutboxEvent
