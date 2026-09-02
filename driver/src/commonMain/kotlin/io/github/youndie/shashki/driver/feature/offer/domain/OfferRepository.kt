package io.github.youndie.shashki.driver.feature.offer.domain

import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideView

/**
 * The offer board, as this application sees it.
 *
 * Remote-only and suspend, like the rider's: there is nothing to cache, and an offer that was true
 * two seconds ago is not a thing worth remembering.
 */
public interface OfferRepository {
    /** The offer waiting for this driver, or `null` — which is the ordinary state, not an error. */
    public suspend fun forDriver(driverId: String): OfferView?

    /**
     * The driver's decision.
     *
     * Throws [OfferGone] when the server says the offer is no longer this driver's — a tab that was
     * asleep, an answer that arrived after the cascade moved on.
     */
    public suspend fun answer(
        rideId: String,
        answer: OfferAnswer,
    ): RideView
}

/** The server answered 409: somebody else has it. */
public class OfferGone(
    public val rideId: String,
) : RuntimeException("offer for ride $rideId has gone")
