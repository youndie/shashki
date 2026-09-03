package io.github.youndie.shashki.driver.feature.trip.domain

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripSummaryView

/** The ride this driver accepted, read back from the server and moved along by them. */
public interface TripRepository {
    public suspend fun read(rideId: String): RideView

    /**
     * Tell the server the trip has reached [to].
     *
     * **Only the next state is accepted and the server decides which that is** (B-37). A client that
     * kept its own idea of the order would disagree with the server the first time somebody pressed
     * a button twice, and the answer that matters is the one that settles the money.
     */
    public suspend fun advance(
        rideId: String,
        driverId: String,
        to: RideStatus,
    ): RideView

    /** What the trip that just ended paid — the kit's D5 (B-70). 404 until the settlement has paid it out. */
    public suspend fun summary(
        rideId: String,
        driverId: String,
    ): TripSummaryView
}
