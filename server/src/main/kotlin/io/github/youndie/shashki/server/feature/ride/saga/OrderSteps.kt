package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.workinprogress.petich.InterceptorResult
import ru.workinprogress.petich.OutboxEvent
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPayload
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.SimpleEnrichedPayload

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
 * EXECUTION: the nearest candidate, reserved. **Compensation releases the driver.**
 *
 * This is the step B-12 turns into a suspended offer with a fifteen-second deadline and a cascade.
 * Today it takes the first candidate that can be reserved, because B-11's job is the saga's shape
 * and its compensations, and a step that waits for a human is a different shape (research §1.4a).
 */
public class ReserveDriverStep(
    private val candidates: CandidateSource,
    private val reservations: DriverReservations,
) : OrderStep() {
    override val phase: PetichPhase = PetichPhase.EXECUTION

    override suspend fun intercept(
        petich: Petich,
        payload: OrderPayload,
    ): InterceptorResult {
        val driver =
            candidates
                .candidates(payload.pickup, payload.rideClass)
                .firstOrNull { reservations.reserve(it.driverId, payload.rideId) }
                ?: return InterceptorResult.Compensate("no cars nearby")
        return InterceptorResult.Proceed(
            enrichedPayload =
                SimpleEnrichedPayload(
                    mapOf(Enriched.DRIVER_ID to driver.driverId),
                ),
        )
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
