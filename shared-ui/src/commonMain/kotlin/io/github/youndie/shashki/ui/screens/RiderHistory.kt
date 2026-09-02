package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.youndie.shashki.ui.kompot.AccentBudget
import io.github.youndie.shashki.ui.kompot.LocalAccentBudget
import io.github.youndie.shashki.ui.kompot.TripRow
import io.github.youndie.shashki.ui.kompot.TripRowRenderer

/**
 * R9: the rider's own pages — *trips*, *profile*, and the one screen the server owns.
 *
 * **The pivot is the top level and nothing may nest it** — the kit's rule 5, which B-17 turned into a
 * renderer invariant. Here it is simply the kit's pivot with three items, of which the third hosts
 * whatever the server sent.
 *
 * **`TripRow` is drawn natively here and remains a kompot component**, which is the property rather
 * than a contradiction: D11 gives the server one screen because a list of somebody's own rides has
 * an obvious native version, and the argument for a server-driven screen is a screen with none. The
 * renderer is reused rather than reimplemented, so the two never drift.
 */
@Composable
public fun RiderHistory(
    titles: List<String>,
    trips: List<TripRow>,
    emptyLine: String,
    profile: List<Pair<String, String>>,
    onTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
    promo: @Composable () -> Unit = {},
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics

    KvadrantPivot(
        titles = titles,
        modifier = modifier.fillMaxSize().background(colors.background),
        title = "shashki",
    ) { page ->
        when (page) {
            0 -> Trips(trips, emptyLine, onTrip, metrics.margin)
            1 -> Profile(profile, metrics.margin)
            else -> promo()
        }
    }
}

@Composable
private fun Trips(
    trips: List<TripRow>,
    emptyLine: String,
    onTrip: (String) -> Unit,
    margin: androidx.compose.ui.unit.Dp,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography

    if (trips.isEmpty()) {
        // **The kit's empty list, section 08: one line in the disabled brush and no action.** A
        // button here would be an invitation to fix something the rider has not done wrong — they
        // have simply not taken a ride yet.
        KvadrantText(
            emptyLine,
            Modifier.padding(horizontal = margin, vertical = 24.dp),
            style = type.body.copy(color = colors.border),
        )
        return
    }

    // Each row is the kompot renderer, so the native list and a server-sent one draw identically.
    // The accent budget is per screen and the list is the screen: the first row that asks gets it.
    //
    // **A plain `Column` and not a `LazyColumn`.** The pivot measures its page with an unbounded
    // height, and a lazy container inside one throws — "Vertically scrollable component was measured
    // with an infinity maximum height constraints" — which is what the first version did to the
    // golden. A rider's history here is a handful of rows; when it is not, the list wants a screen
    // of its own with the scroll that comes with it.
    val budget = AccentBudget()
    val renderer = TripRowRenderer()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = margin),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        trips.forEach { row ->
            Column(Modifier.fillMaxWidth().clickable { onTrip(row.id) }) {
                CompositionLocalProvider(LocalAccentBudget provides budget) {
                    // A row has no fields; the controller is the empty one every non-form
                    // renderer here is given.
                    renderer.Render(row, KompotActionHandler { }, NO_FORMS)
                }
            }
        }
    }
}

/** No form on this screen, and the renderers still take a controller. */
private val NO_FORMS = FormController(FormSchema(formId = "none", fields = emptyList()))

/** Name and address off the token, and nothing to edit — the item's own limit. */
@Composable
private fun Profile(
    rows: List<Pair<String, String>>,
    margin: androidx.compose.ui.unit.Dp,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Column(
        Modifier.fillMaxSize().padding(horizontal = margin, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { (label, value) ->
            Column {
                KvadrantText(label, style = type.meta.copy(color = colors.subtle))
                KvadrantText(value, style = type.rowEmphasis)
            }
        }
    }
}
