package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.youndie.kvadrant.components.KvadrantAppBarButton
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * The driver's offer: fifteen seconds to take a ride.
 *
 * **Two-second read.** Fare at 54, remaining seconds at 54, everything else at 17 or below, and the
 * bar draining left to right is the only moving thing on the screen. No map behind it, no photo —
 * "nothing to read below 15".
 *
 * **The countdown is tabular, and the fixture proves it rather than the KDoc.** `pageTitle` carries
 * `tnum`, so `15`, `09` and `01` occupy the same advance and the fare beside them does not jitter as
 * the seconds change. The AC is "the digits do not shift width between frames", and a golden of
 * three values stacked is what shows it.
 *
 * **The bottom strip is the kit's, and it is deliberately not a `KvadrantAppBar`.** The kit draws a
 * 54 dp chrome strip with a bare ring for decline and a filled accent accept — and the open question
 * "may an app bar carry a filled accept button" was answered *simplify* (B-15). Simplify is this: a
 * plain row at the app bar's height, the library's `KvadrantAppBarButton` for the ring, and the
 * accept drawn here. Not the library's bar, with its menu and its mini state, holding a button it
 * was never designed to hold.
 *
 * **Accept is 17 / SemiBold with 0.02 em tracking, black on the accent — as drawn.** The kit's type
 * table says "button 15 / 400" and its own OfferCard markup says otherwise; the artboard is the
 * acceptance, so the markup wins, and this is the one button in the product that is not `body`.
 * B-21's withdrawal holds in the way that matters: it is shashki's composable, not `KvadrantButton`.
 */
@Composable
public fun OfferCard(
    fare: String,
    classAndPayment: String,
    secondsLeft: Int,
    secondsTotal: Int,
    pickup: String,
    pickupMeta: String,
    dropoff: String,
    dropoffMeta: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    val fraction = (secondsLeft.toFloat() / secondsTotal).coerceIn(0f, 1f)

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .border(1.dp, colors.inactive),
    ) {
        // The bar: 6 dp, the accent draining to the accent at 15 %. `KvadrantProgressBar` is the
        // library's 3 dp foreground-coloured line; this is a different object with a different job.
        Row(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            if (fraction > 0f) Box(Modifier.weight(fraction).height(BAR_HEIGHT).background(colors.accent))
            if (fraction < 1f) {
                Box(
                    Modifier
                        .weight(1f - fraction)
                        .height(BAR_HEIGHT)
                        .background(colors.accent.copy(alpha = BAR_TRACK_ALPHA)),
                )
            }
        }

        Column(
            Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    // **The fare is the foreground brush and not the accent** (B-48). The kit drew
                    // this card in dark only, where amber on black is 7.24:1 and reads; the first
                    // light golden of it put the same amber on white at **2.11:1** — the worst
                    // number in the palette, on the one figure a driver has fifteen seconds to
                    // read. The rule that came out of it is one line: accent-coloured *text* is a
                    // control's label, figures take the foreground. The accent still leads this card
                    // — the strip and the accept button are accent *surfaces*, where the ink is
                    // black at 9.95:1.
                    KvadrantText(fare, style = type.pageTitle)
                    KvadrantText(
                        classAndPayment,
                        style = type.rowEmphasis.copy(color = colors.foreground.copy(alpha = CLASS_LINE_ALPHA)),
                    )
                }
                KvadrantText(secondsLeft.toString().padStart(2, '0'), style = type.pageTitle)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Leg(ShashkiIcons.pinPickup, colors.accent, pickup, pickupMeta)
                Leg(ShashkiIcons.pinDropoff, colors.foreground, dropoff, dropoffMeta)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(KvadrantTheme.metrics.appBarHeight)
                .background(colors.chrome)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KvadrantAppBarButton(onClick = onDecline, label = "decline") {
                Image(
                    painter = rememberVectorPainter(ShashkiIcons.close),
                    contentDescription = null,
                    modifier = Modifier.size(KvadrantTheme.metrics.appBarGlyph).align(Alignment.Center),
                    colorFilter = ColorFilter.tint(colors.foreground),
                )
            }
            // **The accept block is centred in the strip, and the cap has to be obeyed for that to
            // happen** (B-83). `weight(1f)` hands a child a *fixed* width, and `widthIn(max = 200)`
            // cannot narrow a fixed constraint — so the bar grew to 293 dp, overran the decline
            // ring's own 48 dp box on the left and stopped 38 dp short on the right. It read as a
            // button shoved left, which is what it was. The weight goes on a wrapper that centres
            // the capped bar inside the space between the ring and the trailing spacer.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = ACCEPT_MAX_WIDTH)
                        .fillMaxWidth()
                        .height(ACCEPT_HEIGHT)
                        .pressableSurface(colors.accent, onClick = onAccept),
                    contentAlignment = Alignment.Center,
                ) {
                    KvadrantText(
                        "accept",
                        style =
                            type.rowEmphasis.copy(
                                color = colors.onAccent,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.02.em,
                            ),
                    )
                }
            }
            // The ring's own width, so the centring above is symmetric about the card rather than
            // about whatever is left over after the ring.
            Spacer(Modifier.width(KvadrantTheme.metrics.appBarButton))
        }
    }
}

@Composable
private fun Leg(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    meta: String,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(LEG_GLYPH),
            colorFilter = ColorFilter.tint(tint),
        )
        Column {
            KvadrantText(title, style = type.rowEmphasis)
            KvadrantText(meta, style = type.meta.copy(color = colors.subtle))
        }
    }
}

private val BAR_HEIGHT = 6.dp
private const val BAR_TRACK_ALPHA = 0.15f
private const val CLASS_LINE_ALPHA = 0.85f
private val LEG_GLYPH = 20.dp
private val ACCEPT_HEIGHT = 38.dp
private val ACCEPT_MAX_WIDTH = 200.dp
