package io.github.youndie.shashki.protocol

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
