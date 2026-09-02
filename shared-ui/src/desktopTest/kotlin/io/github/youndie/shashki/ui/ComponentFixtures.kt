package io.github.youndie.shashki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.components.ClassTile
import io.github.youndie.shashki.ui.components.ClassTileState
import io.github.youndie.shashki.ui.components.OfferCard
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * The kit's ClassTile block: economy selected, comfort default, business unavailable — at the
 * kit's 390 canvas with the 12 dp page margin, one tile gap apart. The acceptance for B-04's first
 * half: the selected tile's ink is black, the unavailable one keeps its height with an em dash.
 */
@ViddikScreenshot(name = "class tile", group = "components", width = 390, height = 844)
@Composable
internal fun ClassTiles(): Unit = ClassTiles(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "class tile light", group = "components", width = 390, height = 844)
@Composable
internal fun ClassTilesLight(): Unit = ClassTiles(dark = false)

@Composable
private fun ClassTiles(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        val metrics = KvadrantTheme.metrics
        Column(
            Modifier
                .fillMaxSize()
                .background(KvadrantTheme.colors.background)
                .padding(metrics.margin),
            verticalArrangement = Arrangement.spacedBy(metrics.tileGap),
        ) {
            ClassTile("economy", "4 min · Kia Rio", "$ 249", ClassTileState.Selected, carRects = 1, onClick = {})
            ClassTile("comfort", "6 min · Skoda Octavia", "$ 389", ClassTileState.Default, carRects = 2, onClick = {})
            ClassTile("business", "no cars nearby", null, ClassTileState.Unavailable, carRects = 3, onClick = {})
        }
    }
}

/**
 * The kit's OfferCard at 09 of 15 — the driver's theme, full bleed, no map behind it. The bar is
 * drawn at 9/15 rather than the kit's illustrative 64 %, because the bar and the number are one
 * value and a golden that showed them disagreeing would be recording a bug as a reference.
 *
 * The address and both distances are the city's, not invented: Slovenska cesta 15 is an address in
 * the extract (14 is not), and 26.3 km · 20 min is what GraphHopper answers for it to Brnik
 * terminal B on the graph B-06 imports.
 */
@ViddikScreenshot(name = "offer card", group = "components", width = 390, height = 844)
@Composable
internal fun Offer(): Unit = Offer(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "offer card light", group = "components", width = 390, height = 844)
@Composable
internal fun OfferLight(): Unit = Offer(dark = false)

@Composable
private fun Offer(dark: Boolean) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        Column(Modifier.fillMaxSize().background(KvadrantTheme.colors.background)) {
            OfferCard(
                fare = "$ 420",
                classAndPayment = "comfort · card",
                secondsLeft = 9,
                secondsTotal = 15,
                pickup = "Slovenska cesta 15",
                pickupMeta = "1.8 km · 3 min from you",
                dropoff = "Airport, terminal B",
                dropoffMeta = "26.3 km · 20 min",
                onAccept = {},
                onDecline = {},
            )
        }
    }
}

/**
 * Three frames of the countdown, stacked, so the AC "the digits do not shift width between frames"
 * is a picture: `15`, `09` and `01` at the tabular `pageTitle`, with the fare beside each. If the
 * right edges of the numbers do not line up, `tnum` is not reaching the face.
 */
@ViddikScreenshot(name = "offer countdown", group = "components", width = 390, height = 844)
@Composable
internal fun OfferCountdown(): Unit = OfferCountdown(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "offer countdown light", group = "components", width = 390, height = 844)
@Composable
internal fun OfferCountdownLight(): Unit = OfferCountdown(dark = false)

@Composable
private fun OfferCountdown(dark: Boolean) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        val colors = KvadrantTheme.colors
        val type = ShashkiTheme.typography
        Column(Modifier.fillMaxSize().background(colors.background)) {
            for (seconds in listOf(15, 9, 1)) {
                OfferCard(
                    fare = "$ 420",
                    classAndPayment = "comfort · card",
                    secondsLeft = seconds,
                    secondsTotal = 15,
                    pickup = "Slovenska cesta 15",
                    pickupMeta = "1.8 km · 3 min from you",
                    dropoff = "Airport, terminal B",
                    dropoffMeta = "26.3 km · 20 min",
                    onAccept = {},
                    onDecline = {},
                )
                Spacer(Modifier.height(KvadrantTheme.metrics.tileGap))
            }
            KvadrantText(
                "right edges of 15 · 09 · 01 must align — tnum",
                style = type.meta.copy(color = colors.subtle),
                modifier = Modifier.padding(horizontal = KvadrantTheme.metrics.margin),
            )
        }
    }
}
