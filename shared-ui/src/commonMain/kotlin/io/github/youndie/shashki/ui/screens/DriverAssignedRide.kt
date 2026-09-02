package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme

/** What the driver took, as plain values. */
public data class DriverAssignedRideState(
    val status: String,
    val fare: String,
    val pickup: String,
    val dropoff: String,
    val legMeta: String,
)

/**
 * D2, minus the half this repository cannot honestly draw.
 *
 * **There is no map here and no "I have arrived" button, and both absences are decisions.**
 * Turn-by-turn is out of scope for the reference (B-23) and nothing has changed that; the trip's
 * own transitions — `ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED` — have no route on the server
 * yet, so a button for them would post to nothing. A screen that shows what was accepted and what
 * the server currently says about it is the true version of this screen today; a screen with dead
 * controls on it would be the false one.
 */
@Composable
public fun DriverAssignedRide(
    state: DriverAssignedRideState,
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
        KvadrantText(state.status, style = type.meta.copy(color = colors.accent))
        KvadrantText(state.fare, style = type.pageTitle)

        Column {
            KvadrantText(state.pickup, style = type.rowEmphasis)
            KvadrantText("pickup", style = type.meta.copy(color = colors.subtle))
        }
        Column {
            KvadrantText(state.dropoff, style = type.rowEmphasis)
            // **Both rows are labelled, and the second one had to be told twice.** The first golden
            // put "pickup" under one address and the leg's distance under the other, which reads as
            // two different kinds of row rather than as a from and a to.
            KvadrantText("dropoff · ${state.legMeta}", style = type.meta.copy(color = colors.subtle))
        }
    }
}

private val MARGIN = 12.dp
private val GAP = 12.dp
private val TOP = 24.dp
