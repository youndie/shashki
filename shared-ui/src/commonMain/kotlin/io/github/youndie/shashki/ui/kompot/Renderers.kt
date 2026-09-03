package io.github.youndie.shashki.ui.kompot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.registry.KompotComponentMarker
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.protocol.FareBreakdown
import io.github.youndie.shashki.protocol.TripRow
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * The kit's composition rules, where they can actually be enforced.
 *
 * **A protocol can describe the allowed shape; only a renderer can decide what happens to the
 * disallowed one** (research §1.7). Server-side validation would work today and would put the
 * guarantee on the wrong side of the wire: a second implementation of the server drops it silently,
 * and a client that trusted it draws two accent surfaces with nothing reported anywhere.
 *
 * Three rules, one per renderer below, each degrading rather than throwing — which is also kompot's
 * own posture, so this goes with the toolkit's grain rather than against it.
 */
@KompotComponentMarker
public class TripRowRenderer : KompotComponentRenderer<TripRow> {
    @Composable
    override fun Render(
        component: TripRow,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val colors = KvadrantTheme.colors
        val type = ShashkiTheme.typography
        // **Rule 1.** The first row that asks gets the accent; every later one gets chrome. Nothing
        // throws and nothing is dropped — the screen still says what it was sent to say.
        val accented = component.accent && LocalAccentBudget.current.claim(component.id)
        val surface = if (accented) colors.accent else colors.chrome
        val ink = if (accented) colors.onAccent else colors.foreground

        Row(
            Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                KvadrantText(component.title, style = type.rowEmphasis.cappedForCard().copy(color = ink))
                KvadrantText(
                    component.meta,
                    style = type.meta.cappedForCard().copy(color = if (accented) ink else colors.subtle),
                )
            }
            KvadrantText(component.amount, style = type.rowEmphasis.cappedForCard().copy(color = ink))
        }
    }
}

/**
 * **Rule 3.** A figure the server marks primary is drawn at 54 and everything else in the card is
 * capped at 19 — the kit forbids anything between, and forbids a card carrying two figures.
 *
 * The cap is applied by [cappedForCard] rather than by choosing smaller slots by hand, because the
 * rule is about the *card*, not about which slot a line happened to be written with: a caption that
 * arrived as `stateHeadline` is still a caption.
 */
@KompotComponentMarker
public class FareBreakdownRenderer : KompotComponentRenderer<FareBreakdown> {
    @Composable
    override fun Render(
        component: FareBreakdown,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val colors = KvadrantTheme.colors
        val type = ShashkiTheme.typography

        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KvadrantText(
                component.amount,
                style = if (component.primary) type.pageTitle else type.figure,
            )
            KvadrantText(component.caption, style = type.body.cappedForCard().copy(color = colors.subtle))
            for (line in component.lines) {
                // The server may name a step of the ramp; the cap is what the rule adds to it. A
                // line asking for `figure` gets 19, not 32 — the card already has its one figure.
                val asked = line.emphasis.toStyle(type) ?: type.meta
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KvadrantText(line.label, style = type.meta.cappedForCard().copy(color = colors.subtle))
                    KvadrantText(line.value, style = asked.cappedForCard())
                }
            }
        }
    }
}

/**
 * **Rule 2.** Four columns, a 12 dp gap, sizes 1, 2 and 4 — and a size the grid has no shape for is
 * **dropped rather than guessed**. Rounding 3 down to 2 would put a tile where the kit never places
 * one and make the grid reflow, which is the thing the rule forbids by name.
 *
 * **The drop is unobservable and that is a gap in the toolkit, not a choice here.** kompot's
 * `KompotDegradationSink` exists precisely because "a hole is reported by nobody", but its three
 * kinds are all about a type or an action being unknown; there is none for a property outside its
 * allowed set. Reporting this through `UNRENDERABLE_COMPONENT` would be a lie — the component is
 * perfectly renderable, its size is not.
 */
@KompotComponentMarker
public class EarningsTileRenderer : KompotComponentRenderer<EarningsTile> {
    @Composable
    override fun Render(
        component: EarningsTile,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val width = component.size.toTileWidth() ?: return

        val colors = KvadrantTheme.colors
        val type = ShashkiTheme.typography
        val accented = component.accent && LocalAccentBudget.current.claim(component.id)
        val surface = if (accented) colors.accent else colors.chrome
        val ink = if (accented) colors.onAccent else colors.foreground

        Column(
            Modifier
                .width(width)
                .background(surface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A tile's figure is secondary by the kit's rule: 54 belongs to the one primary figure of
            // the screen, and a grid of them would be four page titles.
            KvadrantText(component.figure, style = type.figure.copy(color = ink))
            KvadrantText(component.label, style = type.tileLabel.cappedForCard().copy(color = ink))
        }
    }
}

/**
 * A name the server sent, or `null` for one this ramp has no step for — which is dropped to the
 * default rather than guessed, the same rule as an unknown tile size and for the same reason.
 */
private fun String?.toStyle(type: io.github.youndie.shashki.ui.ShashkiTypography): TextStyle? =
    when (this) {
        "page_title" -> type.pageTitle
        "figure" -> type.figure
        "state_headline" -> type.stateHeadline
        "tile_label" -> type.tileLabel
        "row_emphasis" -> type.rowEmphasis
        "body" -> type.body
        "meta" -> type.meta
        else -> null
    }

/**
 * The kit's grid, as widths: four columns at 74.25 dp with a 12 dp gap, which is what
 * `ShashkiMetrics` draws — this item takes no decision of its own about 4/3, it inherits
 * [B-15](../../../../../../../../docs/backlog/B-15-answer-the-kits-open-questions.md)'s.
 */
private fun Int.toTileWidth(): Dp? =
    when (this) {
        1 -> TILE_SMALL
        2 -> TILE_MEDIUM
        4 -> TILE_WIDE
        else -> null
    }

/**
 * Nothing in a card above 19. A style already at or below it is untouched, so a `meta` line stays
 * `meta` — the cap is a ceiling, not a size.
 */
@Composable
private fun TextStyle.cappedForCard(): TextStyle {
    val ceiling = ShashkiTheme.typography.tileLabel.fontSize
    return if (fontSize.isSpecified && ceiling.isSpecified && fontSize.value > ceiling.value) {
        copy(fontSize = ceiling)
    } else {
        this
    }
}

private val TILE_SMALL = 74.25.dp
private val TILE_MEDIUM = 157.5.dp
private val TILE_WIDE = 324.dp
