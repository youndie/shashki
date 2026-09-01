package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.workinprogress.petich.PetichPayload

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
    public const val REJECTION: String = "rejection"
}

/** The saga's `type` column, and the only string that names it. */
public const val ORDER_SAGA_TYPE: String = "order"
