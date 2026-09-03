package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.LabelledAppBarButton
import io.github.youndie.shashki.ui.map.MapPane
import io.github.youndie.shashki.ui.map.MapScene

/** The driver, as the rider sees them while the car is on its way or on the road. */
public data class TripDriver(
    val name: String,
    val car: String,
    val plate: String,
    val rating: String,
    val carRects: Int,
)

/**
 * What the trip is doing, and the one line the panel leads with.
 *
 * The three follow `RideStatus` rather than inventing a parallel vocabulary: `ARRIVING → ARRIVED →
 * IN_PROGRESS` is the trip, and a screen with a fourth state would be a screen ahead of the server.
 */
public enum class TripStage { ARRIVING, ARRIVED, IN_PROGRESS }

/**
 * The rider watching the car: the route in its two phases, the car on it, and who is driving.
 *
 * **This screen's artboard was not read, and saying so is the point.** Every other screen in this
 * module is transcribed from the kit — R4's 360 dp map, D3's action bar, the 12 dp margin — and the
 * research's rule is that what was verified is separated from what was assumed. The kit's trip
 * screen was not among the files opened during the research, so the *panel* here is built from the
 * composition rules that were recorded (§1.7: one accent surface per screen; a row leads with a
 * route stack, one 20 dp glyph or nothing; figures at 32 and 54) rather than from the drawing. The
 * map half is not a guess: the route's two phases, their colours and their width come from the style
 * documents, and a test holds them to it.
 *
 * So this is a screen that behaves and is composed correctly and whose *layout* is provisional. When
 * the artboard is read, what changes is arrangement, not structure — and the golden is what will
 * show it.
 */
@Composable
public fun RiderTripInProgress(
    scene: MapScene,
    stage: TripStage,
    headline: String,
    meta: String,
    driver: TripDriver,
    actionLabel: String,
    onCall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** The fare, shown once under the figure during the trip — the kit's `420 ₽` line (B-77). */
    fare: String? = null,
    /**
     * R7·a: the car has gone quiet (B-80). The kit's rule: **a full-width band, never a floating
     * card, and the map dims to 40 %**. `null` while positions arrive.
     */
    gpsLost: String? = null,
    /**
     * R10 over the top, or `null` (B-43).
     *
     * **The same confirmation as the wait's, and this is where it costs money.** Cancelling before a
     * driver is assigned compensates the order saga and charges nothing; from here a driver has set
     * off, and the fee in [prompt] is the number the settlement is about to take.
     */
    prompt: CancelPrompt? = null,
    onConfirmPrompt: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            // More map than R4 gives it: this is the screen the rider watches rather than reads, and
            // the panel below carries three rows where R4's carries three tiles and a payment line.
            Box(Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
                MapPane(scene, Modifier.fillMaxSize())
                // "map desaturates to 40 %": a wash of the page's ground at 60 % over it, which is what
                // a canvas that draws its own tiles can do without a colour matrix behind every one.
                if (gpsLost != null) Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = DIM_ALPHA)))
            }

            // The band. Full width, at the join between the map and the panel, in chrome — never a
            // floating card, and never red: red is reserved for cancel and decline.
            gpsLost?.let {
                KvadrantText(
                    it,
                    Modifier
                        .fillMaxWidth()
                        .background(
                            colors.chrome,
                        ).padding(horizontal = metrics.margin, vertical = 10.dp),
                    style = type.body,
                )
            }

            Column(
                Modifier.weight(1f).padding(start = metrics.margin, top = 20.dp, end = metrics.margin),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // **The figure is the minutes to the car, in the accent, while the car is on its
                    // way** (B-76) — the kit's DriverCard puts `3 min` there in cyan. Once the car
                    // has arrived and once the trip is running the figure is a state or a duration,
                    // and the accent goes back to being nobody's.
                    KvadrantText(
                        headline,
                        style =
                            type.figure.copy(
                                color =
                                    if (stage ==
                                        TripStage.ARRIVING
                                    ) {
                                        colors.accent
                                    } else {
                                        colors.foreground
                                    },
                            ),
                    )
                    KvadrantText(meta, style = type.body.copy(color = colors.subtle))
                }
                // The fare, once: what the trip costs, under the figure that says when it ends.
                fare?.let { KvadrantText(it, style = type.rowEmphasis) }

                DriverRow(driver)

                // **The plate is set as a plate — contrast background, SemiBold, wide tracking — and it
                // is the only inverted element on a rider screen** (B-76). It used to be the accent
                // surface, which spent the screen's one accent on the wrong thing: the kit gives the
                // accent to the minutes above and inverts the plate so it is found first anyway.
                Box(
                    Modifier
                        .background(colors.foreground)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    KvadrantText(
                        driver.plate,
                        style =
                            type.rowEmphasis.copy(
                                color = colors.background,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = PLATE_TRACKING,
                            ),
                    )
                }

                Spacer(Modifier.height(0.dp))
            }

            TripBar(stage, actionLabel, onCall, onCancel)
        }

        CancelPromptBox(prompt, onConfirmPrompt, onDismissPrompt)
    }
}

/** Name, car and rating beside the class glyph — the kit's row shape: one glyph, then text. */
@Composable
private fun DriverRow(driver: TripDriver) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Column {
        Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.foreground.copy(alpha = HAIRLINE_ALPHA)))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberVectorPainter(ShashkiIcons.car(driver.carRects)),
                contentDescription = null,
                modifier = Modifier.size(ROW_GLYPH),
                colorFilter = ColorFilter.tint(colors.foreground),
            )
            Column(Modifier.weight(1f)) {
                KvadrantText(driver.name, style = type.body)
                KvadrantText(driver.car, style = type.meta.copy(color = colors.subtle))
            }
            KvadrantText(driver.rating, style = type.meta.copy(color = colors.subtle))
        }
    }
}

/**
 * The same action row R4 and D3 use — ring, label, overflow dots — with the ring calling the driver.
 *
 * The stage decides the label beside the ring rather than a fourth control: a bar whose buttons
 * appeared and disappeared as the trip advanced would be a bar the rider has to re-read.
 */
@Composable
private fun TripBar(
    stage: TripStage,
    actionLabel: String,
    onCall: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .height(KvadrantTheme.metrics.appBarHeight)
            .background(colors.chrome)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One control for the ring and its words (B-71), as on R4.
        LabelledAppBarButton(actionLabel, ShashkiIcons.card, onClick = onCall)
        // Cancelling is behind the dots once the trip has started: the kit puts destructive actions
        // in the overflow, and a trip in progress is not cancelled by a control the thumb rests on.
        KvadrantText(
            if (stage == TripStage.IN_PROGRESS) "···" else "cancel",
            Modifier.clickable(onClick = onCancel),
            style =
                if (stage == TripStage.IN_PROGRESS) {
                    type.tileLabel.copy(color = colors.border)
                } else {
                    type.meta.copy(color = colors.accent)
                },
        )
    }
}

/** More than R4's 360: the trip screen is watched rather than read. */
private val MAP_HEIGHT = 440.dp

/** R7·a dims the map "to 40 %": the ground over it at this alpha. */
private const val DIM_ALPHA = 0.6f

/** The kit's plate: 0.06 em, "wide tracking". */
private val PLATE_TRACKING = 0.06.em
private val ROW_GLYPH = 20.dp
private val HAIRLINE = 1.dp
private const val HAIRLINE_ALPHA = 0.12f
