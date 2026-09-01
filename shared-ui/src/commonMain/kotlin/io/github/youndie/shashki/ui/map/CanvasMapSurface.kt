package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.map.tiles.MvtTile
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.github.youndie.shashki.ui.map.tiles.TileRenderer

/**
 * B-01's fourth route, as far as a prototype takes it: the map drawn by us, inside the caller's
 * bounds, on the same canvas as everything above it.
 *
 * **The point it proves is the one the other three cannot.** There is no second surface, no hole
 * punched in anything, no pointer-event bargain — `modifier` means what it means everywhere else in
 * Compose, so `RiderClassPicker`'s 360 dp map is 360 dp. And because it is an ordinary composable,
 * a screen containing it appears in a viddik golden, which is the acceptance this project runs on.
 *
 * **It is a prototype and the gap is named**: one tile, scaled to the pane, no camera, no cache and
 * no style interpreter. Labels are drawn, because they were the piece §1.8 called hardest. Research §1.8b lists what the rest costs — pmtiles ranges, tile
 * selection, clipping at seams, label collision — and this exists so that estimate is made against
 * something that ran rather than something imagined.
 */
public class CanvasMapSurface(
    private val tile: MvtTile,
    palette: TilePalette = TilePalette.Dark,
) : MapSurface {
    private val renderer = TileRenderer(palette)

    @Composable
    override fun Map(
        scene: MapScene,
        modifier: Modifier,
    ) {
        val measurer = rememberTextMeasurer()
        val labelStyle =
            ShashkiTheme.typography.meta.copy(color = KvadrantTheme.colors.subtle)
        Canvas(modifier) {
            with(renderer) {
                drawTile(tile)
                drawStreetLabels(tile, measurer, labelStyle)
            }
        }
    }
}
