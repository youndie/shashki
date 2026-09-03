package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.ui.kompot.AccentBudget
import io.github.youndie.shashki.ui.kompot.EarningsTileRenderer
import io.github.youndie.shashki.ui.kompot.LocalAccentBudget

// **One grid for D2 and D6** (B-81). It was D6's private helper; the shift screen draws the same
// tiles now — hours online, today's takings, the rating — and a second copy of the row-breaking
// rule is how the third tile came to hang off the edge once already.

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
public fun EarningsTileGrid(tiles: List<EarningsTile>) {
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
internal fun List<EarningsTile>.inRowsOfFourColumns(): List<List<EarningsTile>> {
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

/** The tiles carry no fields; the controller is the empty one every non-form renderer here is given. */
private val NO_FORMS = FormController(FormSchema(formId = "none", fields = emptyList()))
