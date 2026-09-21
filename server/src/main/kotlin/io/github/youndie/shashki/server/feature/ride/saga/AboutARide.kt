package io.github.youndie.shashki.server.feature.ride.saga

/**
 * A saga payload that is about a ride, and therefore knows which one (B-97).
 *
 * **It exists because the outbox id is a routing key, not a name.** Every event this server publishes
 * is keyed `"<rideId>:<suffix>"`, and the consumer takes the ride back out with
 * `substringBeforeLast(':')` — so an event built from the saga's own id routes correctly for the
 * order saga, whose id **is** the ride id, and wrongly for the settlement saga, whose id is
 * `"<rideId>:settlement"` or `"<rideId>:tip"`. `substringBeforeLast` would then file it under
 * `"<rideId>:settlement"`, a ride nobody has.
 *
 * So the handler that publishes on behalf of a saga asks the payload rather than the row. Two
 * classes carry a `rideId` and neither said so in a type; this is that sentence.
 */
public interface AboutARide {
    public val rideId: String
}
