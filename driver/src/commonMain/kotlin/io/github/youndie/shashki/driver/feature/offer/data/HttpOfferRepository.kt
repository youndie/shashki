package io.github.youndie.shashki.driver.feature.offer.data

import io.github.youndie.shashki.driver.feature.offer.domain.OfferGone
import io.github.youndie.shashki.driver.feature.offer.domain.OfferRepository
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

/**
 * The server, over HTTP, through the same `@Resource` classes it routes with.
 *
 * **Two statuses are read rather than left to `.body()`, and both are real answers.** 404 on the
 * board means "no offer", which is what a driver sees for most of a shift; 409 on an answer means
 * the offer moved on. Deserialising either would throw about JSON and lose what the server said.
 */
public class HttpOfferRepository(
    private val client: HttpClient,
) : OfferRepository {
    override suspend fun forDriver(driverId: String): OfferView? {
        val response = client.get(DriverOffers.ForDriver(driverId = driverId))
        return if (response.status == HttpStatusCode.NotFound) null else response.body()
    }

    override suspend fun answer(
        rideId: String,
        answer: OfferAnswer,
    ): RideView {
        val response = client.post(DriverOffers.Answer(rideId = rideId)) { setBody(answer) }
        if (response.status == HttpStatusCode.Conflict) throw OfferGone(rideId)
        return response.body()
    }
}
