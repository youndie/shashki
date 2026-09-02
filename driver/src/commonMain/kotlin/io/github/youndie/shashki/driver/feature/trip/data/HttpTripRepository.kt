package io.github.youndie.shashki.driver.feature.trip.data

import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get

/** `GET /api/rides/{id}`, the same route the rider reads its own ride from. */
public class HttpTripRepository(
    private val client: HttpClient,
) : TripRepository {
    override suspend fun read(rideId: String): RideView = client.get(Rides.ById(id = rideId)).body()
}
