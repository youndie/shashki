package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
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
 * **The scene is drawn in two halves, and the split is the kit's rule rather than convenience.** The
 * basemap and the route go on the canvas; the cars and the pins are composables laid over it,
 * because [CarMarker] says markers do not rotate with the map and a marker painted into the map
 * layer is a marker that would. It is also what lets a pin be the kit's own glyph in the kit's own
 * ink rather than a shape redrawn in a `DrawScope`.
 *
 * **It is a prototype and the gap is named**: one tile, scaled to the pane, no camera, no cache and
 * no style interpreter. Research §1.8b lists what the rest costs — pmtiles ranges, tile selection,
 * clipping at seams, label collision — and this exists so that estimate is made against something
 * that ran rather than something imagined.
 */
public class CanvasMapSurface(
    /**
     * The basemap, or **`null` for none**.
     *
     * Null is not a degraded mode, it is the honest one until tile fetching exists (§1.8b): the
     * background is the style document's own, and everything the server actually said — the road,
     * the car, the pins — is drawn on it, in the right place. A surface that had refused to draw
     * without a tile would have made the trip screen wait on a transport nobody has written.
     */
    private val tile: MvtTile? = null,
    /**
     * Which tile the drawing is projected through, or `null` to take it from the scene's camera.
     *
     * Fixed for a golden of one known tile; derived for an application, where the ride decides where
     * the map is looking.
     */
    private val coordinate: TileCoordinate? = null,
    private val palette: TilePalette = TilePalette.Dark,
) : MapSurface {
    private val renderer = TileRenderer(palette)

    @Composable
    override fun Map(
        scene: MapScene,
        modifier: Modifier,
    ) {
        val measurer = rememberTextMeasurer()
        val labelStyle = ShashkiTheme.typography.meta.copy(color = KvadrantTheme.colors.subtle)
        // The projection needs the drawn size, and the markers need the projection — so the size is
        // read once from the layout and shared, rather than each half measuring for itself.
        var side by remember { mutableStateOf(0f) }
        val frame = coordinate ?: TileCoordinate.containing(scene.camera.centre, scene.camera.zoom.toInt())
        val projection = remember(side, frame) { TileProjection(frame, side) }

        Box(modifier.clipToBounds().onSizeChanged { side = maxOf(it.width, it.height).toFloat() }) {
            Canvas(Modifier.fillMaxSize()) {
                with(renderer) {
                    if (tile == null) {
                        drawRect(palette.background, size = size)
                    } else {
                        drawTile(tile)
                        drawStreetLabels(tile, measurer, labelStyle)
                    }
                    scene.route?.let { drawRoute(it, projection) }
                }
            }
            if (side > 0f) {
                for (pin in scene.pins) {
                    Marker(projection.toCanvas(pin.at), pin.glyph(), PIN_SIZE, KvadrantTheme.colors.foreground)
                }
                for (car in scene.cars) {
                    Marker(
                        at = projection.toCanvas(car.at),
                        glyph = ShashkiIcons.car(rects = 1),
                        size = CAR_SIZE,
                        tint = if (car.self) KvadrantTheme.colors.accent else KvadrantTheme.colors.foreground,
                        rotation = car.bearingDegrees,
                    )
                }
            }
        }
    }

    /** One glyph centred on a point of the map. Rotation turns the glyph, never the layer. */
    @Composable
    private fun Marker(
        at: Offset,
        glyph: ImageVector,
        size: Dp,
        tint: Color,
        rotation: Float = 0f,
    ) {
        val density = LocalDensity.current
        val half = with(density) { size.toPx() / 2 }
        Image(
            painter = rememberVectorPainter(glyph),
            contentDescription = null,
            modifier =
                Modifier
                    .offset(
                        x = with(density) { (at.x - half).toDp() },
                        y = with(density) { (at.y - half).toDp() },
                    ).size(size)
                    .rotate(rotation),
            colorFilter = ColorFilter.tint(tint),
        )
    }

    private fun MapPin.glyph(): ImageVector =
        when (kind) {
            MapPin.Kind.PICKUP -> ShashkiIcons.pinPickup
            MapPin.Kind.DROPOFF -> ShashkiIcons.pinDropoff
        }

    private companion object {
        /** The kit's row glyph size, which is what the pins are drawn at in the artboards. */
        val PIN_SIZE = 20.dp
        val CAR_SIZE = 20.dp
    }
}
