package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.map.tiles.MemoryTileSource
import io.github.youndie.shashki.ui.map.tiles.MvtTile
import io.github.youndie.shashki.ui.map.tiles.PmtilesArchive
import io.github.youndie.shashki.ui.map.tiles.RangeReader
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.github.youndie.shashki.ui.map.tiles.TileSource
import io.github.youndie.shashki.ui.map.tiles.decodeMvt
import io.github.youndie.shashki.ui.portable
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import io.github.youndie.shashki.ui.screens.RiderTripInProgress
import io.github.youndie.shashki.ui.screens.TripDriver
import io.github.youndie.shashki.ui.screens.TripStage
import kotlinx.coroutines.runBlocking
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/** The fixture tile: the city centre, out of the archive rather than out of a loose file. */
internal val cityTile: MvtTile by lazy {
    checkNotNull(cityTiles.loaded(CITY_TILE)) { "the fixture archive does not hold $CITY_TILE" }
}

/**
 * The 2x2 block, read out of a real pmtiles archive.
 *
 * **The goldens go through the archive reader**, which is the difference between a picture of the
 * renderer and a picture of the product: header, root directory, Hilbert ids and the hand-written
 * inflater are all in the path that produces these images. The bytes arrive from memory rather than
 * from HTTP because a screenshot suite has no business opening a socket — the transport is a port
 * and this is the other implementation of it.
 */
internal val cityTiles: TileSource by lazy {
    val bytes =
        checkNotNull(TilePaletteAnchor::class.java.classLoader.getResourceAsStream(FIXTURE_ARCHIVE))
            .use { it.readBytes() }
    runBlocking {
        val archive = PmtilesArchive.open(ByteArrayRangeReader(bytes))
        val decoded = mutableMapOf<TileCoordinate, MvtTile>()
        for (at in FIXTURE_TILES) archive.tile(at)?.let { decoded[at] = decodeMvt(it) }
        MemoryTileSource(decoded)
    }
}

/** The archive the goldens are drawn from — `map/pmtiles_subset.py` cut it out of `city.pmtiles`. */
internal const val FIXTURE_ARCHIVE: String = "tiles/ljubljana.pmtiles"

/**
 * What is in it: the 2x2 block over the city centre, and one tile from the outskirts.
 *
 * The odd one out is `8850/5815`, which is there as an **oracle** rather than for a picture — the
 * loose `.mvt` beside it was extracted for B-01 by a different tool chain, so a reader that walks
 * this archive correctly hands back exactly those bytes.
 */
internal val FIXTURE_TILES: List<TileCoordinate> =
    listOf(
        TileCoordinate(14, 8852, 5825),
        TileCoordinate(14, 8853, 5825),
        TileCoordinate(14, 8852, 5826),
        TileCoordinate(14, 8853, 5826),
        TileCoordinate(14, 8850, 5815),
    )

/** The archive, already in hand. The measured transport is B-07's and is not a screenshot's business. */
internal class ByteArrayRangeReader(
    private val bytes: ByteArray,
) : RangeReader {
    override suspend fun read(
        offset: Long,
        length: Int,
    ): ByteArray = bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
}

private class TilePaletteAnchor

/**
 * Route 4, drawn: a real tile out of `city.pmtiles` on a Compose canvas.
 *
 * **This image is the argument.** Routes 1, 2 and 3 draw the map on a surface Compose does not own,
 * so no golden of theirs can exist — a viddik capture of one of those screens has a hole where the
 * map is, exactly like `screens_rider_class_picker` does today. This one is a golden like any other,
 * on every host, which is the acceptance this project already runs on.
 */
@ViddikScreenshot(name = "canvas tile dark", group = "map", width = 390, height = 390)
@Composable
internal fun CanvasTileDark() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTiles, TilePalette.Dark)) {
            MapPane(emptyScene(MapCamera(TILE_CENTRE)), Modifier.fillMaxSize())
        }
    }
}

/**
 * **Four tiles meeting in the middle of the pane, which is where a tiling renderer fails first.**
 *
 * A road that stops dead at a boundary, a shift of a pixel between neighbours, one tile's water
 * painted over the next one's street: none of those are visible in a golden of a comfortable tile
 * centre, and all of them are visible here. The camera sits exactly on the corner the four fixture
 * tiles share.
 *
 * The ordering rule this checks is the one the renderer had to be split for — every tile's areas,
 * then every tile's roads. Drawn tile by tile instead, the overlap the format leaves at each edge
 * becomes a band of one tile's landcover over its neighbour's road network.
 */
@ViddikScreenshot(name = "canvas tiles at a seam", group = "map", width = 390, height = 390)
@Composable
internal fun CanvasTilesAtASeam() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTiles, TilePalette.Dark)) {
            MapPane(emptyScene(MapCamera(SEAM_CENTRE)), Modifier.fillMaxSize())
        }
    }
}

@ViddikScreenshot(name = "canvas tile light", group = "map", width = 390, height = 390)
@Composable
internal fun CanvasTileLight() {
    val latin = kvadrantLatin()
    RiderTheme(dark = false, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTiles, TilePalette.Light)) {
            MapPane(emptyScene(MapCamera(TILE_CENTRE)), Modifier.fillMaxSize())
        }
    }
}

/**
 * R4 with route 4 behind it — the same screen as `screens_rider_class_picker`, with the hole filled.
 *
 * The two images side by side are what B-01's decision is made on: the map is a *sized* element
 * here, 360 of the 844 dp, because a Compose canvas honours the modifier it is given.
 */
@ViddikScreenshot(name = "rider class picker on canvas", group = "map", width = 390, height = 844)
@Composable
internal fun RiderClassPickerOnCanvas() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTiles, TilePalette.Dark)) {
            RiderClassPicker(
                scene = emptyScene(MapCamera(TILE_CENTRE)),
                destination = "airport",
                destinationMeta = "26.3 km · 20 min",
                offers =
                    listOf(
                        RideClassOffer("economy", "4 min · Kia Rio", "$ 249", carRects = 1),
                        RideClassOffer("comfort", "6 min · Skoda Octavia", "$ 389", carRects = 2),
                        RideClassOffer("business", "no cars nearby", null, carRects = 3, available = false),
                    ),
                selectedIndex = 0,
                paymentLabel = "card ·· 4417",
                orderLabel = "order · $ 249",
                onSelect = {},
                onChangePayment = {},
                onOrder = {},
            )
        }
    }
}

/**
 * The centre of the fixture tile.
 *
 * **It used to be Ljubljana's own centre and that was wrong in a way nothing could see.** The
 * prototype took the frame from a fixed `TileCoordinate` and ignored the camera, so a camera
 * pointing three tiles east made no difference to the picture. With a real viewport the camera picks
 * the tiles, and a fixture pointing at ground the archive does not hold draws an empty map.
 */
private val TILE_CENTRE = GeoPoint(46.04653, 14.51294)

/**
 * Where all four fixture tiles meet: the corner of 8850/5815, 8851/5815, 8850/5816 and 8851/5816.
 *
 * The seam is where a tiling renderer looks wrong first — a road that stops at the edge, a shift of
 * a pixel between neighbours, one tile's water over another's street — so B-30 asks for a golden of
 * exactly this and not of a comfortable tile centre.
 */
private val SEAM_CENTRE = GeoPoint(46.04273565, 14.52392578)

/**
 * Which tile `cityTile` is: the one over the city centre, which is where the demonstration route is
 * lifted from. The projection needs it — without a coordinate a route in latitude and longitude has
 * nowhere to land.
 */
internal val CITY_TILE: TileCoordinate = TileCoordinate(zoom = 14, x = 8852, y = 5825)

/**
 * The demonstration trip, lifted out of the tile's own road geometry.
 *
 * **Typing in coordinates would have produced a route beside the road rather than on it**, and a
 * golden of that looks almost right — which is the worst thing a screenshot test can look. So the
 * longest road in the tile is decoded, turned back into latitude and longitude through the same
 * projection that will draw it, and split: the first stretch is behind the car, the rest is ahead.
 */
internal val tripScene: MapScene by lazy {
    val layer = checkNotNull(cityTile.layer("transportation")) { "the fixture tile has no roads" }
    val flat = checkNotNull(layer.features.flatMap { it.paths }.maxByOrNull { it.size }) { "no path in the tile" }
    val projection = TileProjection(CITY_TILE, layer.extent.toFloat())
    val line =
        (0 until flat.size / 2).map {
            projection.toGeo(
                Offset(flat[it * 2].toFloat(), flat[it * 2 + 1].toFloat()),
            )
        }

    val split = (line.size * TRAVELLED_FRACTION).toInt().coerceIn(1, line.size - 2)
    MapScene(
        camera = MapCamera(line[split]),
        // The two phases share the point the car is on, so the strokes meet under it rather than
        // leaving a gap — which is what `line-cap: butt` in both layers is for.
        route = RouteLine(travelled = line.take(split + 1), ahead = line.drop(split)),
        cars =
            listOf(
                CarMarker(
                    id = "sim-1",
                    at = line[split],
                    bearingDegrees = bearing(line[split], line[split + 1]),
                    self = false,
                ),
            ),
        pins = listOf(MapPin(line.first(), MapPin.Kind.PICKUP), MapPin(line.last(), MapPin.Kind.DROPOFF)),
    )
}

/** Degrees clockwise from north, which is what a glyph rotation wants. */
private fun bearing(
    from: GeoPoint,
    to: GeoPoint,
): Float {
    val dLon = (to.lon - from.lon) * kotlin.math.cos(from.lat * kotlin.math.PI / 180.0)
    val dLat = to.lat - from.lat
    return (kotlin.math.atan2(dLon, dLat) * 180.0 / kotlin.math.PI).toFloat()
}

private const val TRAVELLED_FRACTION = 0.4

private val TRIP_DRIVER =
    TripDriver(name = "Matej", car = "Skoda Octavia · grey", plate = "LJ 84-2KM", rating = "4.9", carRects = 2)

/** The trip screen with the map under it — B-25's acceptance, in the theme the rider sees. */
@ViddikScreenshot(name = "rider trip in progress", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderTripInProgressDark() {
    TripFixture(dark = true, palette = TilePalette.Dark)
}

@ViddikScreenshot(name = "rider trip in progress light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderTripInProgressLight() {
    TripFixture(dark = false, palette = TilePalette.Light)
}

/**
 * The same screen with an empty scene, which is B-25's third criterion made visible: the map is
 * there and carries no route, no car and no pins, because the scene said so. A renderer that drew
 * its fixture regardless would look identical to the two above and this image is what says it does
 * not.
 */
@ViddikScreenshot(name = "rider trip on an empty scene", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderTripOnEmptyScene() {
    TripFixture(dark = true, palette = TilePalette.Dark, scene = emptyScene(MapCamera(TILE_CENTRE)))
}

/**
 * And the same, light (B-48).
 *
 * **The palette moves with the theme.** A light screen on the dark basemap is the defect this item
 * would most easily ship — the two palettes belong to the styles, not to a preference — so the
 * fixture that pairs them is the one that would catch it.
 */
@ViddikScreenshot(name = "rider trip on an empty scene light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderTripOnEmptySceneLight() {
    TripFixture(dark = false, palette = TilePalette.Light, scene = emptyScene(MapCamera(TILE_CENTRE)))
}

@Composable
private fun TripFixture(
    dark: Boolean,
    palette: TilePalette,
    scene: MapScene = tripScene,
) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTiles, palette)) {
            RiderTripInProgress(
                scene = scene,
                stage = TripStage.IN_PROGRESS,
                headline = "airport",
                meta = "18 min · 22.8 km",
                driver = TRIP_DRIVER,
                actionLabel = "call the driver",
                onCall = {},
                onCancel = {},
            )
        }
    }
}
