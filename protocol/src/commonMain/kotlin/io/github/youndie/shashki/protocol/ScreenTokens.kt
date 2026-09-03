package io.github.youndie.shashki.protocol

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/**
 * The names the server may use when it sends a screen, and the client resolves.
 *
 * **A vocabulary rather than values, and it is on the wire, so it is here.** kompot's tokens are open
 * strings on purpose — the toolkit refuses to assume a design system — which means the *agreement*
 * about which strings exist is the product's, and an agreement written twice is an agreement that
 * drifts. The server names a role; what it looks like is the kit's business and is decided in the
 * client. That is the property that makes a server-driven screen safe: a backend cannot paint an
 * unreadable screen, because it cannot name a colour at all.
 *
 * Deliberately small. These are the slots shashki's ramp and palette actually have; a token nobody
 * resolves falls back, and a token nobody sends is a name to maintain for nothing.
 */
public object ShashkiTokens {
    /** 54 / W200 — a page title or the one primary figure of a screen. */
    public const val TYPE_PAGE_TITLE: String = "page_title"

    /** 32 / W200 — every secondary figure. */
    public const val TYPE_FIGURE: String = "figure"

    /** 24 / W300 — the headline of a state screen. */
    public const val TYPE_STATE_HEADLINE: String = "state_headline"

    /** 19 / W300 — tile labels and pivot items. */
    public const val TYPE_TILE_LABEL: String = "tile_label"

    /** 15 / W400 — body copy. */
    public const val TYPE_BODY: String = "body"

    /** 14 / W400 — row subtitles and meta. */
    public const val TYPE_META: String = "meta"

    /** The page's ground. */
    public const val COLOR_BACKGROUND: String = "background"

    /** The ink on it. */
    public const val COLOR_FOREGROUND: String = "foreground"

    /** The quieter ink, for meta. */
    public const val COLOR_SUBTLE: String = "subtle"

    /** The one accent of a screen — the kit allows one, and a tree that asks twice gets chrome. */
    public const val COLOR_ACCENT: String = "accent"

    /** The ink that goes on the accent. */
    public const val COLOR_ON_ACCENT: String = "on_accent"

    /** The strip a bar or a tile sits on. */
    public const val COLOR_CHROME: String = "chrome"

    /**
     * Every typography name, for whoever has to check that all of them behave.
     *
     * **A list here rather than in a test**, because a guard that enumerates a vocabulary by hand
     * stops covering it the day somebody adds a word — which is how three routes of seven came to be
     * round-tripped (B-45). A token added above and not here is a token this list makes obvious.
     */
    public val TYPOGRAPHY: List<String> =
        listOf(TYPE_PAGE_TITLE, TYPE_FIGURE, TYPE_STATE_HEADLINE, TYPE_TILE_LABEL, TYPE_BODY, TYPE_META)

    /** Every colour name, for the same reason. */
    public val COLORS: List<String> =
        listOf(COLOR_BACKGROUND, COLOR_FOREGROUND, COLOR_SUBTLE, COLOR_ACCENT, COLOR_ON_ACCENT, COLOR_CHROME)
}

/**
 * `GET /api/screens/promo` — a screen the server owns entirely.
 *
 * **The only server-driven screen in this product, and that is the decision** (research §2 D11): it
 * has no native version, so it is the one place where a client meeting a component it does not know
 * has to keep drawing — which is the property kompot's degradation exists for and the only thing
 * that makes the kit's composition rules load-bearing rather than decorative. Nothing in the ride
 * flow depends on it.
 */
@Resource("/api/screens/promo")
public class PromoScreen

/**
 * What a client could not render, told to the server that sent it.
 *
 * **kompot's degradation is correct and silent, which is the problem it leaves behind.** A component
 * a build does not know renders as a placeholder and the screen keeps working — so a server can go on
 * sending something half its users see a hole where, for as long as nobody looks. `kompot-client`
 * builds `KompotDegradationSink` for exactly this and leaves the transport to the application
 * (B-32, B-39).
 *
 * `kind` is kompot's own enum name rather than a copy of it: the vocabulary belongs upstream and a
 * second spelling here would be one to keep in step.
 */
@Serializable
public data class DegradationReport(
    val kind: String,
    val componentType: String,
    /** Which screen it happened on, so a count can be read as "this screen is broken for somebody". */
    val screen: String,
    /** Whether anything was drawn in its place. A hole and a placeholder are different holes. */
    val drawnAsFallback: Boolean = false,
)

/** `POST /api/screens/degradations` — what a client could not draw. */

@Resource("/api/screens/degradations")
public class Degradations
