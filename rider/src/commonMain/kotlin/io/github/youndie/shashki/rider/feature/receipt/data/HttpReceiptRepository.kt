package io.github.youndie.shashki.rider.feature.receipt.data

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.rider.feature.receipt.domain.ReceiptRepository
import io.github.youndie.shashki.ui.kompot.shashkiKompotJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText

/**
 * The tree, over HTTP.
 *
 * **`bodyAsText` and then kompot's own decoder**, for the reason `HttpPromoRepository` gives in full:
 * the polymorphic bases are plain interfaces with no generated serializer, so a reified `body<T>()`
 * resolves by reflection on the JVM and throws in the browser this actually runs in.
 */
public class HttpReceiptRepository(
    private val client: HttpClient,
) : ReceiptRepository {
    override suspend fun receipt(rideId: String): KompotComponent =
        shashkiKompotJson().decodeKompotComponent(client.get(Rides.Receipt(id = rideId)).bodyAsText())
}
