package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme

/** D5 as plain values: what the driver earned, and the lines it came from. */
public data class DriverTripSummaryState(
    /** `trip complete`, or the kit's D4·a `passenger cancelled` (B-80). */
    val status: String,
    /** `+$ 23.17` — the figure, already signed. */
    val earned: String,
    /** `card · today $ 46.32`. */
    val meta: String,
    /** Label-left, value-right, in the order the kit lists them: fare, the fee, the tip, the leg. */
    val lines: List<Pair<String, String>>,
)

/**
 * D5: the trip that just ended, from the driver's side (B-70).
 *
 * **The figure is what he earned, not what the passenger paid, and the fee is shown, never hidden**
 * — the kit's own sentence under this screen. R8 is its mirror: the rider sees the fare and gives a
 * tip; the driver sees the share and the cut that made it.
 *
 * **It is the screen's one accent surface**, so the figure takes the driver's amber and the bar
 * below is chrome — the kit's rule 1 applied the other way round from D3, where the bar is the
 * accent and the figure is not.
 *
 * Nothing here computes: every string arrived formatted from the view model, which formatted what
 * the server had already added up.
 */
@Composable
public fun DriverTripSummary(
    state: DriverTripSummaryState,
    /** "Next offer arrives automatically. You stay online." — the kit's line, and the one action. */
    onBackToShift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = MARGIN),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Spacer(Modifier.height(TOP))
        KvadrantText(state.status, style = type.meta.copy(color = colors.subtle))
        KvadrantText(state.earned, style = type.pageTitle.copy(color = colors.accent))
        KvadrantText(state.meta, style = type.body.copy(color = colors.subtle))

        Spacer(Modifier.height(GAP))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, value) in state.lines) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KvadrantText(label, style = type.body.copy(color = colors.subtle))
                    KvadrantText(value, style = type.body)
                }
            }
        }

        Spacer(Modifier.height(GAP))
        KvadrantText(
            "Next offer arrives automatically. You stay online.",
            style = type.meta.copy(color = colors.subtle),
        )

        Spacer(Modifier.weight(1f))

        // The bar is chrome, not the accent: the figure above has the screen's one accent surface,
        // and a filled bar under it would be the second the kit forbids.
        Row(
            Modifier
                .fillMaxWidth()
                .height(BAR)
                .background(colors.chrome)
                .clickable(onClick = onBackToShift),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KvadrantText("back to the shift", style = type.body)
        }
        Spacer(Modifier.height(MARGIN))
    }
}

private val MARGIN = 12.dp
private val GAP = 12.dp
private val TOP = 24.dp
private val BAR = 54.dp
