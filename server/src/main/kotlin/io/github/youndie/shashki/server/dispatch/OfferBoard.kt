package io.github.youndie.shashki.server.dispatch

import java.util.concurrent.ConcurrentHashMap

/** An offer on the board: which ride is asking which driver, and until when. */
public data class Offer(
    val rideId: String,
    val driverId: String,
    val expiresAtEpochMs: Long,
)

/**
 * Which driver is currently being asked about which ride — the thing the driver's app polls.
 *
 * **A cache, not a record.** The saga's row is the record (`offer.driverId` and `offer.expiresAt`
 * in its enriched payload); this is what makes "what is my offer" a map lookup rather than a scan
 * over JSON columns. Lost on restart, and that costs at most one offer's worth of seconds: the
 * saga is still suspended, the sweeper's TTL still runs, and the next answer or expiry re-drives it.
 * The same shape B-20's geo-index takes, for the same reason.
 */
public interface OfferBoard {
    public fun post(offer: Offer)

    public fun withdraw(rideId: String)

    public fun forDriver(driverId: String): Offer?

    public fun forRide(rideId: String): Offer?
}

public class InMemoryOfferBoard : OfferBoard {
    private val byRide = ConcurrentHashMap<String, Offer>()

    override fun post(offer: Offer) {
        byRide[offer.rideId] = offer
    }

    override fun withdraw(rideId: String) {
        byRide.remove(rideId)
    }

    override fun forDriver(driverId: String): Offer? = byRide.values.firstOrNull { it.driverId == driverId }

    override fun forRide(rideId: String): Offer? = byRide[rideId]
}
