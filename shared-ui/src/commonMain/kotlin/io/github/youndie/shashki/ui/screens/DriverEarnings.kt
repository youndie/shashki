package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kvadrant.components.KvadrantPivot
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.BackBar
import io.github.youndie.shashki.ui.kompot.AccentBudget
import io.github.youndie.shashki.ui.kompot.EarningsTile
import io.github.youndie.shashki.ui.kompot.EarningsTileRenderer
import io.github.youndie.shashki.ui.kompot.LocalAccentBudget

/**
 * D6: what the driver has earned — today, this week, and everything.
 *
 * **The primary figure is the page's, and the tiles are secondary.** The kit's section 08 rule 3
 * allows one `54` per screen; today's sum is it, drawn as the page's own figure above the grid, and
 * the tiles below carry `32`s through `EarningsTileRenderer`. A grid of `54`s would be four page
 * titles.
 *
 * **The tiles are kompot's component drawn natively**, exactly as R9 draws `TripRow` and for the
 * same reason: a screen with an obvious native version does not need a server to describe it, and
 * reusing the renderer is what stops the two drifting apart.
 */
@Composable
public fun DriverEarnings(
    titles: List<String>,
    today: String,
    todayLabel: String,
    tiles: List<EarningsTile>,
    history: List<Pair<String, String>>,
    emptyLine: String,
    modifier: Modifier = Modifier,
    /**
     * How to leave, or `null` where the platform already offers it (B-67).
     *
     * A browser has a back button people already use; a window has nothing, and this screen is
     * pushed rather than started at. `AddressBar.providesBack` is the question, asked once in the
     * application rather than guessed at here.
     */
    onBack: (() -> Unit)? = null,
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Column(modifier.fillMaxSize().background(colors.background)) {
        KvadrantPivot(
            titles = titles,
            modifier = Modifier.weight(1f),
            title = "earnings",
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        Modifier.padding(horizontal = metrics.margin, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column {
                            KvadrantText(today, style = type.pageTitle)
                            KvadrantText(todayLabel, style = type.body.copy(color = colors.subtle))
                        }
                        Tiles(tiles)
                    }
                }

                1 -> {
                    Column(Modifier.padding(horizontal = metrics.margin, vertical = 12.dp)) { Tiles(tiles.drop(1)) }
                }

                else -> {
                    History(history, emptyLine, metrics.margin)
                }
            }
        }

        // The kit's bar, and only where the platform has no back of its own (B-67).
        onBack?.let { BackBar(it) }
    }
}

/**
 * The kit's four-column grid: the renderer knows the widths, this knows the gap — **and where the
 * row ends**.
 *
 * This used to be a single `Row`. The client sends three tiles of two columns each, which is six
 * columns into a grid four wide: the third tile hung off the right edge with its figure wrapped one
 * character to a line. Nothing caught it, because the fixture that photographs this screen sends
 * **two** tiles — a golden is a photograph, and it never had the third in the frame.
 *
 * "Tiles do not reflow" is a rule about a tile's own size, not a licence for a row to be wider than
 * the screen: a size outside 1/2/4 is dropped by the renderer, and a row that would exceed four
 * columns starts a new one here.
 */
@Composable
private fun Tiles(tiles: List<EarningsTile>) {
    val budget = AccentBudget()
    val renderer = EarningsTileRenderer()
    // The accent budget is the screen's and not the row's: rule 1 allows one accent surface per
    // screen, so it is claimed across every row rather than once per line.
    CompositionLocalProvider(LocalAccentBudget provides budget) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.inRowsOfFourColumns().forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> renderer.Render(tile, KompotActionHandler { }, NO_FORMS) }
                }
            }
        }
    }
}

/**
 * Greedy, in the order the server sent them: a tile goes on the current row while it fits and starts
 * the next one when it does not. Re-ordering to pack them tighter would be the client deciding what
 * the screen says first, which is the server's to decide.
 */
private fun List<EarningsTile>.inRowsOfFourColumns(): List<List<EarningsTile>> {
    val rows = mutableListOf<MutableList<EarningsTile>>()
    var used = COLUMNS + 1
    forEach { tile ->
        val size = tile.size.coerceIn(1, COLUMNS)
        if (used + size > COLUMNS) {
            rows += mutableListOf(tile)
            used = size
        } else {
            rows.last() += tile
            used += size
        }
    }
    return rows
}

/** The kit's grid, in columns. `EarningsTileRenderer` holds the same number as widths. */
private const val COLUMNS = 4

/** Rides and what each one paid. The kit's empty list: one line, disabled brush, no action. */
@Composable
private fun History(
    rows: List<Pair<String, String>>,
    emptyLine: String,
    margin: androidx.compose.ui.unit.Dp,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography

    if (rows.isEmpty()) {
        KvadrantText(
            emptyLine,
            Modifier.padding(horizontal = margin, vertical = 24.dp),
            style = type.body.copy(color = colors.border),
        )
        return
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { (label, amount) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KvadrantText(label, style = type.body)
                KvadrantText(amount, style = type.rowEmphasis)
            }
        }
    }
}

private val NO_FORMS = FormController(FormSchema(formId = "none", fields = emptyList()))
