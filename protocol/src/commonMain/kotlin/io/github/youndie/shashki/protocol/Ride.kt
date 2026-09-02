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
    /**
     * What cancelling **now** would cost, in cents, or `null` when the ride cannot be cancelled at
     * all.
     *
     * **The number rather than the rule** (B-43). R10 shows the amount before the button, and the
     * amount is a quarter of the fare once a driver has set off and nothing before — a rule that
     * lives in `Commission` on the server. A client that multiplied by 0.25 itself would be a second
     * copy of a pricing rule, and the first coefficient change would have the screen promising one
     * number and the settlement charging another.
     *
     * `0` and `null` are different answers: free to cancel, and too late to cancel.
     */
    val cancellationFeeCents: Long? = null,
    /**
     * What was actually taken, once the settlement has taken it (B-44).
     *
     * **The quote is what the ride was going to cost and this is what it did.** R8 shows a sum, and
     * showing the quote there would be a screen that is right until the first cancellation fee — a
     * quarter of the fare, charged and displayed as the whole of it.
     */
    val chargedCents: Long? = null,
    /**
     * What this rider already said about the driver, or `null` for a ride nobody has rated (B-59).
     *
     * **R8 was write-only.** It drew five empty stars over a ride that carried a five, so a refresh
     * — or a pasted link — invited a second rating of the same journey, and the server took it. A
     * screen that does not read back what it wrote is a screen that does not know the ride happened.
     */
    val stars: Int? = null,
    /**
     * What paid for it. The kit's R8 meta names the card, and this is where that comes from (B-59).
     *
     * It is the id the request carried, not a card number: this product has no card, and printing
     * one would be the fabrication its object-store item refused in another place.
     */
    val paymentMethodId: String? = null,
    /**
     * When the rider asked for this ride (B-61).
     *
     * **The number, and the client formats it.** R9's rows carry a date and its months group the
     * list, and both are presentation: a server that sent "28 aug · 19:40" would be deciding a
     * locale and a timezone on behalf of a browser that knows both.
     */
    val requestedAtEpochMs: Long? = null,
)

/**
 * The ride routes, declared once. `POST /api/rides` asks for a car; `GET /api/rides/{id}` reads it.
 *
 * Both sides build from this class, so the path exists as a string in exactly one place — here —
 * and a route renamed is a route the compiler notices.
 */
@Resource("/api/rides")
public class Rides(
    /**
     * `GET /api/rides?mine=true` — the rider's own, newest first (B-45).
     *
     * **A parameter on the collection rather than a route of its own**, because "which rides" is a
     * filter on the same resource and `/api/rides/mine` would be a path segment competing with an
     * id. Absent means the collection is not being asked for at all: this server has no "everybody's
     * rides" and answers 400 rather than inventing one.
     */
    public val mine: Boolean? = null,
) {
    /**
     * `POST /api/rides/{id}/rating` — the kit's R8, first half (B-44).
     *
     * Refused before `COMPLETED`: a rating of a ride that has not ended is a rating of nothing.
     */
    @Resource("{id}/rating")
    public class Rate(
        public val parent: Rides = Rides(),
        public val id: String,
    )

    /**
     * `POST /api/rides/{id}/tip` — R8's second half.
     *
     * **A charge and not a bigger capture**: the hold was the quote and it is gone by the time this
     * is called. See the settlement's `Kind.TIP`.
     */
    @Resource("{id}/tip")
    public class Tip(
        public val parent: Rides = Rides(),
        public val id: String,
    )

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

    /**
     * `GET /api/rides/{id}/history` — what happened to this ride, from the events alone.
     *
     * **Read from a projection of the broker's topic and not from the saga's row**, which is the
     * whole point of it existing: a consumer that can say what a ride went through, built only from
     * what was published, is the difference between a broker and a log line (B-38).
     */
    @Resource("{id}/history")
    public class History(
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

/**
 * One thing that happened to a ride, as a consumer of the topic sees it.
 *
 * **Deliberately not the event's own payload.** `ride.assigned` and `ride.settled` carry different
 * fields and there will be more of them; a history is a *sequence*, and a reader asking "what
 * happened" wants the order and the names rather than every field of each. The payloads stay on the
 * topic for anybody who wants them.
 */
@Serializable
public data class RideEventView(
    val type: String,
    val offset: Long,
)

/** Everything the projection knows about a ride, in the order the broker gave it. */
@Serializable
public data class RideHistoryView(
    val rideId: String,
    val events: List<RideEventView>,
)

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

/** What the rider thought of it: one to five, and nothing else — R8 has no comment box. */
@Serializable
public data class RideRating(
    val stars: Int,
)

/**
 * What the rider gave on top, in cents.
 *
 * **Cents and not a percentage**, because the screen offers amounts and the server must not have to
 * agree with the client about what 10% of a fare is. The fare is already known to both of them; the
 * number that moves money is the one that travels.
 */
@Serializable
public data class RideTip(
    val amountCents: Long,
)

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
