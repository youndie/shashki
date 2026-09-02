package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import io.github.youndie.shashki.ui.map.tiles.NoTiles
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.github.youndie.shashki.ui.map.tiles.TileRenderer
import io.github.youndie.shashki.ui.map.tiles.TileSource

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
 * **It is no longer one tile.** The prototype drew a single tile scaled to the pane, which was
 * enough to answer "can Compose draw this map" and not enough to be a map: B-30 gave it a camera, a
 * plane of tiles and a source to fetch them from. What §1.8b still lists as unbuilt is the style
 * interpreter and label collision — the colours and filters here are transcribed Kotlin, and two
 * street names that overlap both get drawn.
 */
public class CanvasMapSurface(
    /**
     * Where the basemap comes from. [NoTiles] is a real answer and not a degraded one: the
     * background is the style document's own, and everything the server actually said — the road,
     * the car, the pins — is drawn on it in the right place. A surface that refused to draw without
     * tiles would make every screen wait on a transport.
     */
    private val tiles: TileSource = NoTiles,
    private val palette: TilePalette = TilePalette.Dark,
    /**
     * How big one tile is drawn, in pixels at the pane's own density.
     *
     * **512 rather than the pane's width**, which is what the prototype used. A tile sized to the
     * pane makes zoom mean something different on every screen; a fixed size makes `zoom` mean what
     * it means in every other tile scheme, so `MapCamera(centre, 14.0)` frames the same ground on a
     * 390 dp phone pane and a 900 dp window.
     */
    private val tileSide: Float = DEFAULT_TILE_SIDE,
) : MapSurface {
    private val renderer = TileRenderer(palette)

    @Composable
    override fun Map(
        scene: MapScene,
        modifier: Modifier,
    ) {
        val measurer = rememberTextMeasurer()
        val labelStyle = ShashkiTheme.typography.meta.copy(color = KvadrantTheme.colors.subtle)
        // The viewport needs the drawn size, and the markers need the viewport — so the size is read
        // once from the layout and shared, rather than each half measuring for itself.
        var pane by remember { mutableStateOf(Size.Zero) }
        val projection =
            remember(pane, scene.camera) { MapViewport(scene.camera, pane.width, pane.height, tileSide) }
        val wanted = remember(projection) { projection.tiles() }

        // **Fetching is beside the drawing, not inside it.** The canvas paints what is in memory;
        // this asks for what is not, and the source's own state brings the frame back when it lands.
        LaunchedEffect(wanted, tiles) {
            for (coordinate in wanted) tiles.load(coordinate)
        }

        Box(modifier.clipToBounds().onSizeChanged { pane = Size(it.width.toFloat(), it.height.toFloat()) }) {
            Canvas(Modifier.fillMaxSize()) {
                with(renderer) {
                    drawRect(palette.background, size = size)
                    val present = wanted.mapNotNull { at -> tiles.loaded(at)?.let { at to it } }
                    // Areas across every tile, then roads across every tile, then the labels. A tile
                    // finished before its neighbour starts paints water over the neighbour's roads
                    // in the band where the two overlap.
                    for ((at, tile) in present) drawTileAreas(tile, projection.originOf(at), projection.drawnTileSide)
                    for ((at, tile) in present) drawTileRoads(tile, projection.originOf(at), projection.drawnTileSide)
                    // **One pass over every tile's labels, not one pass per tile.** A street that
                    // crosses a boundary is two features with one name, so labels can only be
                    // de-duplicated and collision-tested against the whole viewport.
                    drawStreetLabels(
                        present.flatMap { (at, tile) ->
                            streetLabels(tile, projection.originOf(at), projection.drawnTileSide)
                        },
                        measurer,
                        labelStyle,
                    )
                    scene.route?.let { drawRoute(it, projection) }
                }
            }
            if (pane.minDimension > 0f) {
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

        /** One tile, in pixels. The convention every tile scheme uses, doubled for a dense screen. */
        const val DEFAULT_TILE_SIDE = 512f
    }
}
