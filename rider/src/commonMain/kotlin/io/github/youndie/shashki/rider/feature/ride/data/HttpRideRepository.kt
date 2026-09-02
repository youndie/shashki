package io.github.youndie.shashki.rider.feature.ride.data

import io.github.youndie.shashki.protocol.AssignedDriverView
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quotes
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.RouteView
import io.github.youndie.shashki.protocol.Routes
import io.github.youndie.shashki.rider.feature.ride.domain.RideRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody

/**
 * The server, over HTTP, through the same `@Resource` classes it routes with.
 *
 * **No path is written here as a string, and that is the point of `:protocol`.** A renamed route is a
 * compile error on both sides rather than a 404 in front of somebody; the server matches the class
 * and the client builds the URL from it.
 */
public class HttpRideRepository(
    private val client: HttpClient,
) : RideRepository {
    override suspend fun quotes(
        from: GeoPoint,
        to: GeoPoint,
    ): QuotesView = client.post(Quotes()) { setBody(RouteRequest(from = from, to = to)) }.body()

    override suspend fun request(request: RideRequest): RideView = client.post(Rides()) { setBody(request) }.body()

    override suspend fun read(rideId: String): RideView = client.get(Rides.ById(id = rideId)).body()

    override suspend fun cancel(rideId: String) {
        client.post(Rides.Cancel(id = rideId))
    }

    override suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteView = client.post(Routes()) { setBody(RouteRequest(from = from, to = to)) }.body()

    override suspend fun driver(rideId: String): AssignedDriverView = client.get(Rides.Driver(id = rideId)).body()
}
