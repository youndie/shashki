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

/**
 * The order a trip goes in, and the only place it is written down.
 *
 * **On the wire rather than in the server, because both sides need it and a copy would drift.** The
 * server refuses a transition that is not the next one; the driver's client has to know which button
 * to draw. Those are two questions with one answer, and the moment they are two lists the client
 * offers a button the server refuses.
 *
 * A list and not four booleans: the property worth testing is that it is a *sequence*, and a refusal
 * has to be able to name which state was expected.
 */
public object TripProgression {
    public val ORDER: List<RideStatus> =
        listOf(
            RideStatus.ASSIGNED,
            RideStatus.ARRIVING,
            RideStatus.ARRIVED,
            RideStatus.IN_PROGRESS,
            RideStatus.COMPLETED,
        )

    public fun next(from: RideStatus): RideStatus? = ORDER.getOrNull(ORDER.indexOf(from) + 1)

    public fun isNext(
        from: RideStatus,
        to: RideStatus,
    ): Boolean = next(from) == to
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

    /** `POST /api/rides/{id}/cancel`. Before a driver is assigned this is the order saga compensating from the middle. */
    @Resource("{id}/cancel")
    public class Cancel(
        public val parent: Rides = Rides(),
        public val id: String,
    )

    /** `GET /api/rides/{id}/driver` — where the car this ride is waiting for has got to. */
    @Resource("{id}/driver")
    public class Driver(
        public val parent: Rides = Rides(),
        public val id: String,
    )
}

/** What a driver does with an offer. Not answering is not a decision — the server times it out. */
@Serializable
public enum class DriverDecision {
    ACCEPT,
    DECLINE,
}

/** `driverId` is a field until B-09 puts it in the driver's token. */
@Serializable
public data class OfferAnswer(
    val driverId: String,
    val decision: DriverDecision,
)

/**
 * The offer as the driver sees it: the kit's OfferCard, minus the seconds — those the client counts.
 *
 * **Both ends of the deadline are here, and the second one is why.** A client that had only
 * [expiresAtEpochMs] would have to subtract its *own* clock from it, and a browser's clock is
 * whatever the machine says — a laptop an hour out draws a countdown an hour wrong, or fifteen
 * seconds that never start. With [nowEpochMs] beside it the client counts a **duration** it was
 * handed rather than a difference it computed, and the only thing its clock still contributes is
 * the rate at which seconds pass.
 *
 * The deadline is still the server's in the sense that matters: the answer is checked there, and a
 * driver whose tab was asleep gets 409 rather than a ride (see `OfferGoneException`).
 */
@Serializable
public data class OfferView(
    val rideId: String,
    val rideClass: RideClass,
    val quote: Quote,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
    val expiresAtEpochMs: Long,
    val nowEpochMs: Long,
)

/**
 * How a driver moves a trip along: `POST /api/driver/rides/{rideId}/advance`.
 *
 * **One route with a target rather than four verbs**, because the interesting behaviour is the
 * *order*: `ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED`, one step at a time, and a driver who
 * skips one has to be refused. Four verbs would spread that rule across four handlers; one route
 * puts it in one place with one test.
 *
 * The trip is not a saga (research §1.4c): these transitions are the driver's own and there is
 * nothing to compensate. What `COMPLETED` starts *is* a saga, and that is the settlement.
 */
@Resource("/api/driver/rides")
public class DriverRides {
    @Resource("{rideId}/advance")
    public class Advance(
        public val parent: DriverRides = DriverRides(),
        public val rideId: String,
    )
}

/** `driverId` is a field until B-09 puts it in the driver's token, like every other one here. */
@Serializable
public data class TripAdvance(
    val driverId: String,
    /** The state the driver says the trip has reached. Only the next one is accepted. */
    val to: RideStatus,
)

/**
 * The driver's side of matching. `GET /api/driver/offers/{driverId}` is the offer waiting for this
 * driver, or 404; `POST /api/driver/offers/{rideId}/answer` is the driver's decision on it.
 */
@Resource("/api/driver/offers")
public class DriverOffers {
    @Resource("{driverId}")
    public class ForDriver(
        public val parent: DriverOffers = DriverOffers(),
        public val driverId: String,
    )

    @Resource("{rideId}/answer")
    public class Answer(
        public val parent: DriverOffers = DriverOffers(),
        public val rideId: String,
    )
}

/**
 * Where the rider's own car is, while it is on its way.
 *
 * **`null` positions are a real answer, not an error.** A driver's application goes quiet in a lift
 * or a tunnel, and the rider's screen should keep the last thing it drew rather than show a failure
 * — so the absence is in the type and the screen decides what to do with it.
 */
@Serializable
public data class AssignedDriverView(
    val driverId: String,
    val at: GeoPoint? = null,
    val bearingDegrees: Float = 0f,
)
