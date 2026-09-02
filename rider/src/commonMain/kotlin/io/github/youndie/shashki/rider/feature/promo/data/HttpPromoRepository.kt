package io.github.youndie.shashki.rider.feature.promo.data

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.shashki.protocol.PromoScreen
import io.github.youndie.shashki.rider.feature.promo.domain.PromoRepository
import io.github.youndie.shashki.ui.kompot.shashkiKompotJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText

/**
 * The tree, over HTTP.
 *
 * **`bodyAsText` and then kompot's own decoder**, not `body<KompotComponent>()`. The polymorphic
 * bases are plain interfaces with no generated serializer, so a reified call resolves by reflection
 * on the JVM and throws on Wasm — it would pass every test this module has and fail in the browser.
 * kompot-core ships `decodeKompotComponent` to make that a function to call rather than a thing to
 * know, and its own source says so.
 */
public class HttpPromoRepository(
    private val client: HttpClient,
) : PromoRepository {
    override suspend fun promo(): KompotComponent =
        shashkiKompotJson().decodeKompotComponent(client.get(PromoScreen()).bodyAsText())
}
