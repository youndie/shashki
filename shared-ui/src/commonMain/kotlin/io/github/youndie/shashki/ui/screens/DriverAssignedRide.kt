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

/** What the driver took, as plain values. */
public data class DriverAssignedRideState(
    val status: String,
    val fare: String,
    val pickup: String,
    val dropoff: String,
    val legMeta: String,
    /**
     * The one thing the driver can do next, or `null` when the ride is over.
     *
     * **One action and not four buttons**, because a trip is a sequence: the driver is always at
     * exactly one point in it, and offering the other three is offering three refusals.
     */
    val action: String? = null,
    val working: Boolean = false,
)

/**
 * D2, minus the half this repository cannot honestly draw.
 *
 * **There is no map here, and that absence is still a decision.** Turn-by-turn is out of scope for
 * the reference (B-23) and nothing has changed that.
 *
 * **The button, on the other hand, is new and is the point.** B-29 shipped this screen with nothing
 * to press because the server had no route for the trip's transitions, and said so rather than
 * drawing a dead control. B-37 built them, so there is one action here — the next one — and pressing
 * it the last time is what captures the rider's money.
 */
@Composable
public fun DriverAssignedRide(
    state: DriverAssignedRideState,
    onAdvance: () -> Unit,
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

        Spacer(Modifier.weight(1f))

        // The kit's accept strip, reused: a filled accent bar at the app bar's height, drawn here
        // rather than taken from the library — the same answer B-15 gave for `OfferCard`.
        state.action?.let { label ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BAR)
                    .background(if (state.working) colors.inactive else colors.accent)
                    .clickable(enabled = !state.working, onClick = onAdvance),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KvadrantText(label, style = type.body.copy(color = colors.onAccent))
            }
            Spacer(Modifier.height(MARGIN))
        }
    }
}

private val MARGIN = 12.dp
private val GAP = 12.dp
private val TOP = 24.dp
private val BAR = 54.dp
