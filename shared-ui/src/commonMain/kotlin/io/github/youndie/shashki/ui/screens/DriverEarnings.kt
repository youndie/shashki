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
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.protocol.TripRow
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.BackBar
import io.github.youndie.shashki.ui.components.EarningsTileGrid
import io.github.youndie.shashki.ui.kompot.AccentBudget
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
                        EarningsTileGrid(tiles)
                    }
                }

                1 -> {
                    Column(
                        Modifier.padding(horizontal = metrics.margin, vertical = 12.dp),
                    ) { EarningsTileGrid(tiles.drop(1)) }
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
