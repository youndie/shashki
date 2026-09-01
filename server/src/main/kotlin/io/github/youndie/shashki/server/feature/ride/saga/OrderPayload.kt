package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.workinprogress.petich.PetichPayload
import ru.workinprogress.petich.ResumePayload

/**
 * What the order saga starts with: the request, verbatim.
 *
 * **`@SerialName` is load-bearing.** It is the polymorphic discriminator petich-postgres writes into
 * the `payload` column; without it the stored format would be the fully qualified class name, and
 * moving this file would make every saga on disk unreadable. petich says the same about its own.
 */
@Serializable
@SerialName("order")
public class OrderPayload(
    public val rideId: String,
    public val riderId: String,
    public val pickup: GeoPoint,
    public val dropoff: GeoPoint,
    public val rideClass: RideClass,
    public val paymentMethodId: String,
) : PetichPayload()

/**
 * Keys into the saga's enriched payload — the values each step leaves for the ones after it and
 * for its own compensation. `SimpleEnrichedPayload` is a string map, so these are the schema.
 */
public object Enriched {
    public const val QUOTE_DISTANCE: String = "quote.distanceMetres"
    public const val QUOTE_DURATION: String = "quote.durationSeconds"
    public const val QUOTE_AMOUNT: String = "quote.amountCents"
    public const val QUOTE_CURRENCY: String = "quote.currency"
    public const val HOLD_ID: String = "billing.holdId"
    public const val DRIVER_ID: String = "dispatch.driverId"
    public const val OFFER_DRIVER: String = "offer.driverId"
    public const val OFFER_ATTEMPT: String = "offer.attempt"
    public const val OFFER_EXPIRES_AT: String = "offer.expiresAtEpochMs"
    public const val REJECTION: String = "rejection"
}

/** What the saga is waiting for while an offer is out. The driver's app answers it. */
public const val ACTION_DRIVER_ANSWER: String = "DRIVER_ANSWER"

/**
 * A driver's answer, handed to the engine as the resume payload. Not persisted — petich keeps it on
 * the `process` call only — so no serialisation, and no `@SerialName` to get wrong.
 */
public class DriverAnswer(
    public val driverId: String,
    public val outcome: Outcome,
) : ResumePayload() {
    public enum class Outcome { ACCEPT, DECLINE, IGNORED }
}

/** The rider changed their mind while the saga was waiting for a driver. */
public class RiderCancelled(
    public val reason: String = "rider cancelled",
) : ResumePayload()

/** The saga's `type` column, and the only string that names it. */
public const val ORDER_SAGA_TYPE: String = "order"
