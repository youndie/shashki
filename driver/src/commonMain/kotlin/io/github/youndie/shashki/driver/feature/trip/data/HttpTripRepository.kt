package io.github.youndie.shashki.driver.feature.trip.data

import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.protocol.TripAdvance
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody

/** `GET /api/rides/{id}`, the same route the rider reads its own ride from. */
public class HttpTripRepository(
    private val client: HttpClient,
) : TripRepository {
    override suspend fun read(rideId: String): RideView = client.get(Rides.ById(id = rideId)).body()

    override suspend fun advance(
        rideId: String,
        driverId: String,
        to: RideStatus,
    ): RideView =
        client
            .post(DriverRides.Advance(rideId = rideId)) { setBody(TripAdvance(driverId, to)) }
            .body()
}
