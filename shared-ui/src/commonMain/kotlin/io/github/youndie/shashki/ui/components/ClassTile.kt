package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTheme

/** The three states the kit draws, and there is no fourth. */
public enum class ClassTileState { Selected, Default, Unavailable }

/**
 * One service class, as a full-width row — the kit's "wide, 4 columns".
 *
 * **Not a `KvadrantTile`, and the reason is in the artboard.** The kit's markup is a padded row —
 * `14 × 16`, glyph, name over meta, price — and three of them fit under the address on R4. A
 * `TileSize.Wide` is 366 × 177 dp by the metric set, so three would not, and a class picker is not
 * a Start screen. "Wide" here means "spans the four columns", not "is the 2:1 tile".
 *
 * **Selection is the accent fill, not a border or a tick.** Selected: the accent, black ink
 * (`onAccent`, which this product supplies — research §1.1a). Default: chrome, foreground, meta in
 * the subtle brush. Unavailable: chrome, everything in the **disabled** brush, and an em dash where
 * the price was — the row keeps its height "so the thumb lands where it expects to".
 *
 * The name is 19 sp at **W400**, which is what the kit draws, and not the ramp's `tileLabel`
 * (19 / W300): the kit's component markup and its type table disagree on this one weight, and the
 * artboard is the acceptance, so the markup wins and the disagreement is recorded here rather than
 * silently resolved either way.
 *
 * Press feedback is the theme's — the tilt arrives through `LocalIndication`, nothing is applied here.
 */
@Composable
public fun ClassTile(
    name: String,
    meta: String,
    price: String?,
    state: ClassTileState,
    carRects: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    val background = if (state == ClassTileState.Selected) colors.accent else colors.chrome
    val ink =
        when (state) {
            ClassTileState.Selected -> colors.onAccent
            ClassTileState.Default -> colors.foreground
            ClassTileState.Unavailable -> colors.disabled
        }
    val metaInk =
        when (state) {
            ClassTileState.Selected -> colors.onAccent.copy(alpha = SELECTED_META_ALPHA)
            ClassTileState.Default -> colors.subtle
            ClassTileState.Unavailable -> colors.disabled
        }

    Row(
        modifier
            .fillMaxWidth()
            .pressableSurface(background, enabled = state != ClassTileState.Unavailable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberVectorPainter(ShashkiIcons.car(carRects)),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(ink),
        )
        Column(Modifier.weight(1f)) {
            KvadrantText(name, style = type.tileLabel.copy(color = ink, fontWeight = FontWeight.W400))
            KvadrantText(meta, style = type.meta.copy(color = metaInk))
        }
        KvadrantText(price ?: EM_DASH, style = type.figure.copy(color = ink))
    }
}

/** The kit's meta on a selected tile is the ink at 70 %. */
private const val SELECTED_META_ALPHA = 0.7f
private const val EM_DASH = "—"
