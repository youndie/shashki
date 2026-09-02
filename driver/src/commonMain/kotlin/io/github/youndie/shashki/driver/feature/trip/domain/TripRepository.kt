package io.github.youndie.shashki.driver.feature.trip.domain

import io.github.youndie.shashki.protocol.RideView

/** The ride this driver accepted, read back from the server. */
public interface TripRepository {
    public suspend fun read(rideId: String): RideView
}
