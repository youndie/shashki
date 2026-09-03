package io.github.youndie.shashki.protocol

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The three components the server sends as a tree, rather than the client drawing from a model.
 *
 * **They are here, and not beside their renderers, so that a server can build one.** `kompot-core`
 * carries `KompotComponent` and no Compose; `:shared-ui` carries Compose and `:server` can never
 * depend on it. With the declarations in this module the server composes a screen and the client
 * draws it, which is the whole point of a server-driven component — see B-65.
 *
 * **The registry is generated in two halves and they meet at a type argument.** This module runs
 * kompot's processor for the polymorphic serializers module; `:shared-ui` runs it for the renderers,
 * and KSP resolves `KompotComponentRenderer<TripRow>` across the module boundary. B-65 spent a while
 * believing that split was unsupported, on the strength of a build that failed for a different
 * reason — stale imports of the old package — so the belief is recorded there beside its correction.
 *
 * **Everything the kit's composition rules constrain is expressible here, on purpose.** A protocol
 * that could not say "two accent surfaces" would enforce the rule by construction and would also be
 * a protocol nobody could evolve: the server that sends it is a different deployment from the client
 * that draws it, and the rule has to hold when they disagree. So the shape is permissive and
 * [the renderers][io.github.youndie.shashki.ui.kompot] are where the disallowed one is dealt with —
 * see research §1.7 and B-17.
 */
@Serializable
@SerialName("shashki.trip_row")
@KompotComponentMarker
public data class TripRow(
    override val id: String,
    /** One line, for a row with one end or none — a cancelled search, a ride nobody drove. */
    val title: String,
    val meta: String,
    val amount: String,
    /**
     * Both ends of the journey, for the kit's **route stack** (B-78): two lines, each led by its pin,
     * and the amount on the right never changes the stack's shape. `null` draws [title] alone — the
     * kit's rule 4, *a row leads with a route stack, one glyph, or nothing*.
     */
    val from: String? = null,
    val to: String? = null,
    /** Under the amount, small — the kit's `card` / `cash` — so the meta line stays one line (B-78). */
    val note: String? = null,
    /** Whether this row asks for the screen's one accent surface. More than one may ask. */
    val accent: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * A fare, broken into lines.
 *
 * `primary` is the server saying this is *the* figure of the card, which the kit answers with 54;
 * anything else in the card is capped at 19. Both halves of that rule are the renderer's.
 */
@Serializable
@SerialName("shashki.fare_breakdown")
@KompotComponentMarker
public data class FareBreakdown(
    override val id: String,
    val amount: String,
    val caption: String,
    val lines: List<FareLine> = emptyList(),
    val primary: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

/**
 * One line of the breakdown.
 *
 * [emphasis] is the server naming a step of the ramp — `figure`, `state_headline`, `tile_label` — and
 * it is here because the rule needs something able to break it. A card with a second figure in it is
 * exactly what the kit forbids, and a type that could not express one would make the renderer's cap
 * untestable and the claim above untrue.
 */
@Serializable
public data class FareLine(
    val label: String,
    val value: String,
    val emphasis: String? = null,
)

/**
 * One tile of the driver's earnings grid.
 *
 * [size] is in the kit's columns: **1, 2 or 4 of four**, and nothing else. A server that sends 3 is
 * not sending a smaller tile — it is sending something this grid has no shape for, and the rule is
 * that it is dropped rather than guessed at.
 */
@Serializable
@SerialName("shashki.earnings_tile")
@KompotComponentMarker
public data class EarningsTile(
    override val id: String,
    val label: String,
    val figure: String,
    val size: Int,
    val accent: Boolean = false,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent {
    public companion object {
        /** The kit's four-column grid, in the only widths it draws. */
        public val ALLOWED_SIZES: Set<Int> = setOf(1, 2, 4)
    }
}
