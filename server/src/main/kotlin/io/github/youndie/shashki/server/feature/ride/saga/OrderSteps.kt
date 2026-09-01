package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.Offer
import io.github.youndie.shashki.server.dispatch.OfferBoard
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.workinprogress.petich.InterceptorResult
import ru.workinprogress.petich.OutboxEvent
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPayload
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.SimpleEnrichedPayload
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One step per phase, and each is one class because the reason a step exists is the reason it can
 * be undone. The order is petich's; the priorities are all 0 because there is one step per phase.
 */
public abstract class OrderStep : PetichInterceptor<OrderPayload> {
    final override fun supports(payload: PetichPayload): Boolean = payload is OrderPayload

    /** Steps with nothing to undo say so by leaving this alone. */
    override suspend fun compensate(
        petich: Petich,
        payload: OrderPayload,
    ) {}

    protected fun Petich.enriched(key: String): String? = (enrichedPayload as? SimpleEnrichedPayload)?.data?.get(key)

    protected fun Petich.quote(): Quote? {
        val distance = enriched(Enriched.QUOTE_DISTANCE)?.toIntOrNull() ?: return null
        val duration = enriched(Enriched.QUOTE_DURATION)?.toIntOrNull() ?: return null
        val amount = enriched(Enriched.QUOTE_AMOUNT)?.toLongOrNull() ?: return null
        val currency = enriched(Enriched.QUOTE_CURRENCY) ?: return null
        return Quote(distance, duration, amount, currency)
    }
}

/** ENRICHMENT: the route and what it costs. Nothing to undo — a quote is a number. */
public class QuoteStep(
    private val routes: RouteEstimator,
    private val pricing: Pricing,
) : OrderStep() {
    override val phase: PetichPhase = PetichPhase.ENRICHMENT

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val estimate = routes.estimate(payload.pickup, payload.dropoff)
        val quote = pricing.quote(payload.pickup, payload.rideClass, estimate)
        return InterceptorResult.Proceed(
            enrichedPayload =
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
    private val area: ServiceArea = ServiceArea.LJUBLJANA,
) : OrderStep() {
    override val phase: PetichPhase = PetichPhase.VALIDATION

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult =
        when {
            payload.pickup !in area -> InterceptorResult.Reject("pickup is outside the service area")
            payload.dropoff !in area -> InterceptorResult.Reject("dropoff is outside the service area")
            else -> InterceptorResult.Proceed()
        }
}

public data class ServiceArea(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
) {
    public operator fun contains(p: GeoPoint): Boolean = p.lat in south..north && p.lon in west..east

    public companion object {
        /** The city and its airport, generously. B-06 replaces the guess with the extract's bounds. */
        public val LJUBLJANA: ServiceArea = ServiceArea(south = 45.95, north = 46.30, west = 14.35, east = 14.70)
    }
}

/**
 * AUTHORIZATION: a hold for the quoted amount. **Compensation releases it** — this is the step the
 * whole demo is about: die after this and before the driver, and the hold must not survive.
 */
public class HoldPaymentStep(
    private val payments: PaymentGateway,
) : OrderStep() {
    override val phase: PetichPhase = PetichPhase.AUTHORIZATION

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val quote = petich.quote() ?: return InterceptorResult.Reject("no quote to hold against")
        val hold = payments.hold(payload.paymentMethodId, quote.amountCents, quote.currency)
        return InterceptorResult.Proceed(enrichedPayload = SimpleEnrichedPayload(mapOf(Enriched.HOLD_ID to hold.value)))
    }

    override suspend fun compensate(
        petich: Petich,
        payload: OrderPayload,
    ) {
        petich.enriched(Enriched.HOLD_ID)?.let { payments.release(HoldId(it)) }
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
    override val phase: PetichPhase = PetichPhase.EXECUTION
    override val priority: Int = 10

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val first =
            candidates.candidates(payload.pickup, payload.rideClass).firstOrNull {
                reservations.reserve(it.driverId, payload.rideId)
            }
                ?: return InterceptorResult.Compensate(NO_CARS)
        return offer(payload, first.driverId, attempt = 0, suspend = true)
    }

    override suspend fun compensate(
        petich: Petich,
        payload: OrderPayload,
    ) {
        withdraw(petich, payload)
    }

    internal fun offer(
        payload: OrderPayload,
        driverId: String,
        attempt: Int,
        suspend: Boolean,
    ): InterceptorResult {
        val expiresAt = clock.nowEpochMs() + OFFER_SECONDS * MILLIS
        board.post(Offer(payload.rideId, driverId, expiresAt))
        timeouts.schedule(payload.rideId, driverId, OFFER_SECONDS)
        val enriched =
            SimpleEnrichedPayload(
                mapOf(
                    Enriched.OFFER_DRIVER to driverId,
                    Enriched.OFFER_ATTEMPT to attempt.toString(),
                    Enriched.OFFER_EXPIRES_AT to expiresAt.toString(),
                ),
            )
        return if (suspend) {
            InterceptorResult.Suspend(ACTION_DRIVER_ANSWER, enriched, ttl = MATCHING_BUDGET)
        } else {
            InterceptorResult.Resuspend(ACTION_DRIVER_ANSWER, enriched, ttl = MATCHING_BUDGET)
        }
    }

    /** The offer is over and the driver is free again: decline, ignore, cancel, rollback. */
    internal fun withdraw(
        petich: Petich,
        payload: OrderPayload,
    ) {
        withdrawKeepingReservation(petich, payload)
        petich.enriched(Enriched.OFFER_DRIVER)?.let { reservations.release(it, payload.rideId) }
    }

    /** The offer is over because the driver took it: off the board, timer cancelled, reservation kept. */
    internal fun withdrawKeepingReservation(
        @Suppress("UNUSED_PARAMETER") petich: Petich,
        payload: OrderPayload,
    ) {
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
    override val phase: PetichPhase = PetichPhase.EXECUTION
    override val priority: Int = 0

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val offered =
            petich.enriched(Enriched.OFFER_DRIVER) ?: return InterceptorResult.Compensate("resumed with no offer out")
        return when (val answer = petich.resumePayload) {
            is RiderCancelled -> {
                offers.withdraw(petich, payload)
                InterceptorResult.Compensate(answer.reason)
            }

            is DriverAnswer -> {
                if (answer.driverId !=
                    offered
                ) {
                    return InterceptorResult.Resuspend(ACTION_DRIVER_ANSWER, ttl = OfferStep.MATCHING_BUDGET)
                }
                when (answer.outcome) {
                    DriverAnswer.Outcome.ACCEPT -> {
                        offers.withdrawKeepingReservation(petich, payload)
                        InterceptorResult.Proceed(
                            enrichedPayload =
                                SimpleEnrichedPayload(
                                    mapOf(Enriched.DRIVER_ID to offered),
                                ),
                        )
                    }

                    DriverAnswer.Outcome.DECLINE, DriverAnswer.Outcome.IGNORED -> {
                        offers.withdraw(petich, payload)
                        val attempt = (petich.enriched(Enriched.OFFER_ATTEMPT)?.toIntOrNull() ?: 0) + 1
                        val next =
                            candidates
                                .candidates(payload.pickup, payload.rideClass)
                                .drop(attempt)
                                .firstOrNull { reservations.reserve(it.driverId, payload.rideId) }
                                ?: return InterceptorResult.Compensate(OfferStep.NO_CARS)
                        offers.offer(payload, next.driverId, attempt, suspend = false)
                    }
                }
            }

            // Resumed by something that is not an answer — a retried request, say. Nothing changed;
            // keep waiting for the driver who was asked.
            else -> {
                InterceptorResult.Resuspend(ACTION_DRIVER_ANSWER, ttl = OfferStep.MATCHING_BUDGET)
            }
        }
    }

    override suspend fun compensate(
        petich: Petich,
        payload: OrderPayload,
    ) {
        petich.enriched(Enriched.DRIVER_ID)?.let { reservations.release(it, payload.rideId) }
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
    override val phase: PetichPhase = PetichPhase.POST_PROCESSING

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val event =
            RideAssignedEvent(
                rideId = payload.rideId,
                riderId = payload.riderId,
                driverId = petich.enriched(Enriched.DRIVER_ID) ?: error("POST_PROCESSING reached with no driver"),
                quote = petich.quote() ?: error("POST_PROCESSING reached with no quote"),
            )
        return InterceptorResult.Proceed(
            outboxEvents =
                listOf(
                    RideOutboxEvent(
                        id = "${payload.rideId}:assigned",
                        type = RideAssignedEvent.TYPE,
                        payload = json.encodeToString(RideAssignedEvent.serializer(), event),
                    ),
                ),
        )
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
