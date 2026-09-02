package io.github.youndie.shashki.ui.kompot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * **Each of the kit's three renderer-side rules, fed the payload that breaks it.**
 *
 * A fixture that sent a *legal* tree would photograph the rule working and prove nothing: the rules
 * are statements about what happens to the illegal one. So every tree here is one the kit forbids,
 * and each image is the degraded form — nothing throws, nothing is a hole where a rule was broken,
 * and nothing is silently guessed at.
 */
@ViddikScreenshot(name = "two accent surfaces", group = "kompot", width = 390, height = 320)
@Composable
internal fun TwoAccentSurfaces() {
    Fixture {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Both ask. The kit allows one, so the second is drawn in chrome — which is a row that
            // still says what it was sent to say, rather than a missing row or a crash.
            for (row in ACCENT_ROWS) {
                TripRowRenderer().Render(row, NO_ACTIONS, NO_FORMS)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@ViddikScreenshot(name = "a tile size the grid has no shape for", group = "kompot", width = 390, height = 320)
@Composable
internal fun UnknownTileSize() {
    Fixture {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Four tiles are sent and three are drawn: `size = 3` is not a narrower tile, it is a
            // shape this grid does not have, and rounding it to 2 would reflow the row.
            for (tile in TILES) {
                EarningsTileRenderer().Render(tile, NO_ACTIONS, NO_FORMS)
            }
        }
    }
}

@ViddikScreenshot(name = "a second figure in a card", group = "kompot", width = 390, height = 320)
@Composable
internal fun SecondFigureInACard() {
    Fixture {
        // The card has its one figure — the fare, at 54 because the server marked it primary — and a
        // line asking for `figure` as well. The rule caps everything else at 19, so the second
        // figure is drawn as a line rather than competing with the first.
        FareBreakdownRenderer().Render(SECOND_FIGURE, NO_ACTIONS, NO_FORMS)
    }
}

@Composable
private fun Fixture(content: @Composable () -> Unit) {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalAccentBudget provides AccentBudget()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(KvadrantTheme.colors.background)
                    .padding(KvadrantTheme.metrics.margin),
            ) {
                content()
            }
        }
    }
}

private val ACCENT_ROWS =
    listOf(
        TripRow(id = "trip-1", title = "airport", meta = "yesterday · 22.8 km", amount = "$ 389", accent = true),
        TripRow(id = "trip-2", title = "Miklošičeva cesta 4", meta = "monday · 3.1 km", amount = "$ 74", accent = true),
    )

private val TILES =
    listOf(
        EarningsTile(id = "today", label = "today", figure = "$ 128", size = 2, accent = true),
        EarningsTile(id = "rides", label = "rides", figure = "9", size = 1),
        EarningsTile(id = "hours", label = "hours online", figure = "6.5", size = 3),
        EarningsTile(id = "week", label = "this week", figure = "$ 941", size = 4),
    )

private val SECOND_FIGURE =
    FareBreakdown(
        id = "fare",
        amount = "$ 389",
        caption = "comfort · 22.8 km · 20 min",
        primary = true,
        lines =
            listOf(
                FareLine("base", "$ 150"),
                FareLine("distance", "$ 205"),
                FareLine("time", "$ 34", emphasis = "figure"),
            ),
    )

private val NO_ACTIONS = KompotActionHandler { }

/**
 * The renderers here take a `FormController` and none of them uses it — the three components carry
 * no fields. An empty schema is the smallest honest way to satisfy the signature; a `null` would
 * have meant changing kompot's interface to suit a fixture.
 */
private val NO_FORMS = FormController(FormSchema(formId = "none", fields = emptyList()))
