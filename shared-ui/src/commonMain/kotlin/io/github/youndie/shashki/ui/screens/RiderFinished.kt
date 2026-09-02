package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * R8: the ride is over — what it cost, how it was, and whether to add something.
 *
 * **The sum is what was taken, not what was quoted.** They are the same number for a fare and are
 * not for a cancellation fee, which is a quarter of it; a screen that showed the quote there would
 * be right until the first ride that ended early.
 *
 * **Rating without a tip is the common case, and the screen is arranged for it.** *skip* is a
 * first-class button beside the tip row rather than a small grey link under it: a product whose
 * "no thank you" is harder to find than its "yes" is a product doing something else.
 *
 * The stars are five glyphs and the row is the only accent surface on the screen — the kit allows
 * one, and here it is the thing being asked for.
 */
@Composable
public fun RiderFinished(
    total: String,
    destination: String,
    driver: String,
    stars: Int,
    tips: List<String>,
    selectedTip: Int?,
    doneLabel: String,
    onStars: (Int) -> Unit,
    onTip: (Int?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether *skip* is the answer this rider gave (B-59).
     *
     * **Separate from [selectedTip] because "nothing chosen" and "chose to give nothing" are two
     * states and the screen used to have one.** `selectedTip == null` meant skip, so the chip was
     * filled the moment the screen opened and the kit's one accent surface was spent recommending
     * that nothing be paid.
     */
    skipped: Boolean = false,
    /** The card and the journey, in the kit's meta line: `paid with card ·· 4417 · 26 min · 18.4 km`. */
    meta: String? = null,
    /** The sum with the chosen tip, or `null` when none is chosen. */
    totalWithTip: String? = null,
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Column(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.weight(1f).padding(horizontal = metrics.margin, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                KvadrantText(total, style = type.pageTitle)
                KvadrantText(destination, style = type.body.copy(color = colors.subtle))
                // What paid for it and how long it took — the kit's own second line, and a fact
                // about the ride rather than a decoration (B-59).
                meta?.let { KvadrantText(it, style = type.meta.copy(color = colors.subtle)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvadrantText("how was $driver?", style = type.rowEmphasis)
                Stars(stars, onStars)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvadrantText("add a tip", style = type.rowEmphasis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tips.forEachIndexed { index, label ->
                        TipButton(label, selected = index == selectedTip) { onTip(index) }
                    }
                    // **Not a link under the row.** Skipping is the ordinary answer and it is the
                    // same size as the others — and it is filled only once somebody has chosen it,
                    // which is what [skipped] is for.
                    TipButton("skip", selected = skipped) { onTip(null) }
                }
                // **The sum with the tip, which the kit ends this screen on.** A rider who taps a
                // chip and sees no number change has been shown a button rather than a price.
                totalWithTip?.let {
                    KvadrantText("total with tip · $it", style = type.rowEmphasis)
                }
            }
        }

        FinishedBar(doneLabel, onDone)
    }
}

/** Five glyphs, filled to [stars]. Tapping the third means three, which is the whole interaction. */
@Composable
private fun Stars(
    stars: Int,
    onStars: (Int) -> Unit,
) {
    val colors = KvadrantTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..STAR_COUNT).forEach { star ->
            val filled = star <= stars
            Image(
                painter = rememberVectorPainter(ShashkiIcons.star(filled)),
                contentDescription = null,
                modifier = Modifier.size(STAR_SIZE).clickable { onStars(star) },
                colorFilter = ColorFilter.tint(if (filled) colors.accent else colors.subtle),
            )
        }
    }
}

@Composable
private fun TipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Box(
        Modifier
            .width(TIP_WIDTH)
            .height(TIP_HEIGHT)
            .background(if (selected) colors.accent else colors.chrome)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KvadrantText(
            label,
            style = type.body.copy(color = if (selected) colors.onAccent else colors.foreground),
        )
    }
}

/** The same bar every other screen ends with, and the only way off this one. */
@Composable
private fun FinishedBar(
    label: String,
    onDone: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .height(KvadrantTheme.metrics.appBarHeight)
            .background(colors.chrome)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvadrantText(label, Modifier.clickable(onClick = onDone), style = type.body.copy(color = colors.accent))
    }
}

private const val STAR_COUNT = 5
private val STAR_SIZE = 28.dp
private val TIP_WIDTH = 76.dp
private val TIP_HEIGHT = 44.dp
