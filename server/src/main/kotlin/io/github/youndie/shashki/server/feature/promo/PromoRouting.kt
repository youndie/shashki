package io.github.youndie.shashki.server.feature.promo

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.button
import io.github.youndie.kompot.standard.kompotScreen
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.standard.text
import io.github.youndie.shashki.protocol.PromoScreen
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.ktor.http.ContentType
import io.ktor.server.resources.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus

/**
 * `GET /api/screens/promo` — the one screen this server owns.
 *
 * **Auth tier: public.** A promotion is what an anonymous visitor is shown; there is nothing here
 * that belongs to anybody.
 *
 * **It names roles and never values.** `page_title`, `accent`, `body` — the client's design system
 * decides what those look like, so this server cannot paint an unreadable screen even by accident.
 * That property is what makes handing a screen to a backend safe, and it is the toolkit's, not ours.
 *
 * **Encoded here rather than through `ContentNegotiation`.** A kompot tree needs
 * `classDiscriminator = "type"` and the toolkit's polymorphic module; installing those globally
 * would change how every other route serialises to suit one. So this route encodes with kompot's own
 * `Json` and answers a string.
 */
public fun Route.promoRoutes() {
    get<PromoScreen> {
        call.respondText(encodedPromoTree(), ContentType.Application.Json)
    }
}

/**
 * The engine's own modules, minus everything that needs a client.
 *
 * **`encodeKompotComponent` rather than `encodeToString`**, and kompot-core says why in its own
 * source: the polymorphic bases are plain interfaces, so a reified call resolves by reflection on the
 * JVM and throws on Wasm and Native. Writing it the reified way here would work in every test this
 * server has and fail in the browser that reads it.
 */
private val PROMO_JSON =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule
    }

/**
 * The tree.
 *
 * Written in the toolkit's own DSL and out of the toolkit's own components: a column of text and one
 * button. **Nothing product-specific is in it**, which is deliberate for a first server-driven
 * screen — the shashki components exist and their rules are tested, and a promotion is not where a
 * fare breakdown belongs.
 */
internal fun encodedPromoTree(): String = PROMO_JSON.encodeKompotComponent(promoTree())

internal fun promoTree(): KompotComponent =
    kompotScreen {
        spacing(SPACING_DP)

        text("first ride on us", style = TypographyToken(ShashkiTokens.TYPE_PAGE_TITLE), id = "promo-headline")
        text(
            "half of every fare, for a week",
            style = TypographyToken(ShashkiTokens.TYPE_FIGURE),
            color = ColorToken(ShashkiTokens.COLOR_ACCENT),
            id = "promo-figure",
        )
        text(
            "Order a car the way you always do. The discount is applied when the ride finishes and " +
                "appears on the receipt.",
            style = TypographyToken(ShashkiTokens.TYPE_BODY),
            color = ColorToken(ShashkiTokens.COLOR_SUBTLE),
            id = "promo-body",
        )
        // `navigate` and not `open_url`: the toolkit forbids http in a deeplink precisely so a server
        // cannot walk somebody out of the application through an ordinary transition.
        button("order a car", NavigateAction(deeplink = "shashki://rides"), id = "promo-action")
    }

private const val SPACING_DP = 16
