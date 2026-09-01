package io.github.youndie.shashki.protocol

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/**
 * The states a ride passes through, and the only place they are written down.
 *
 * **These are not the saga's phases, and one saga does not own all of them.** `petich` walks
 * `ENRICHMENT → VALIDATION → AUTHORIZATION → EXECUTION → POST_PROCESSING` (research §1.4), and the
 * *order saga* runs that once, from [REQUESTED] to [ASSIGNED]: the quote, the payment hold, the
 * matching and the offer that suspends for a driver (B-11, B-12). What follows — [ARRIVING],
 * [ARRIVED], [IN_PROGRESS] — is the trip, driven by the driver's own transitions and by location, and
 * it is not a saga at all: nothing in it needs compensating. [COMPLETED] opens the second saga, the
 * *settlement*: capture the hold, write the payout, send the receipt, publish the events.
 *
 * `CANCELLED` is reachable from every state before `COMPLETED`, which is why it is not a step in
 * the sequence. Before [ASSIGNED] it is the order saga compensating from the middle — the hold
 * released, the driver freed. After it, it is a trip ending early and a settlement saga that
 * charges a fee instead of a fare. Same word, two mechanisms, and the split is the demo's point.
 */
@Serializable
public enum class RideStatus {
    REQUESTED,
    MATCHING,
    ASSIGNED,
    ARRIVING,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/** The service class a rider chooses, and the only axis pricing varies on today. */
@Serializable
public enum class RideClass {
    ECONOMY,
    COMFORT,
    BUSINESS,
}

/** WGS 84, degrees. Latitude first, as the map libraries this product will meet all expect. */
@Serializable
public data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

/**
 * What a ride is going to cost, and the route it was costed on.
 *
 * Money is an integer count of the smallest unit — cents — because a fare is added, multiplied by a
 * class coefficient and compared, and a `Double` does two of those three wrong.
 */
@Serializable
public data class Quote(
    val distanceMetres: Int,
    val durationSeconds: Int,
    val amountCents: Long,
    val currency: String,
)

/** What a rider sends to ask for a car. `riderId` is a field until B-09 puts it in the token. */
@Serializable
public data class RideRequest(
    val riderId: String,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
    val rideClass: RideClass,
    val paymentMethodId: String,
)

/** A ride as the rider sees it. Everything optional arrives as the saga produces it. */
@Serializable
public data class RideView(
    val id: String,
    val status: RideStatus,
    val rideClass: RideClass,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
    val quote: Quote? = null,
    val driverId: String? = null,
    val cancellationReason: String? = null,
)

/**
 * The ride routes, declared once. `POST /api/rides` asks for a car; `GET /api/rides/{id}` reads it.
 *
 * Both sides build from this class, so the path exists as a string in exactly one place — here —
 * and a route renamed is a route the compiler notices.
 */
@Resource("/api/rides")
public class Rides {
    @Resource("{id}")
    public class ById(
        public val parent: Rides = Rides(),
        public val id: String,
    )
}
