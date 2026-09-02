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
import io.github.youndie.shashki.ui.map.tiles.MvtTile
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.github.youndie.shashki.ui.map.tiles.decodeMvt
import io.github.youndie.shashki.ui.portable
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import io.github.youndie.shashki.ui.screens.RiderTripInProgress
import io.github.youndie.shashki.ui.screens.TripDriver
import io.github.youndie.shashki.ui.screens.TripStage
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/** The fixture tile, decoded once. `z14/8850/5815` out of the city's own archive. */
internal val cityTile: MvtTile by lazy {
    val bytes =
        checkNotNull(TilePaletteAnchor::class.java.classLoader.getResourceAsStream("tiles/ljubljana-14-8850-5815.mvt"))
            .use { it.readBytes() }
    decodeMvt(bytes)
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
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, CITY_TILE, TilePalette.Dark)) {
            MapPane(emptyScene(MapCamera(TILE_CENTRE)), Modifier.fillMaxSize())
        }
    }
}

@ViddikScreenshot(name = "canvas tile light", group = "map", width = 390, height = 390)
@Composable
internal fun CanvasTileLight() {
    val latin = kvadrantLatin()
    RiderTheme(dark = false, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, CITY_TILE, TilePalette.Light)) {
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
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, CITY_TILE, TilePalette.Dark)) {
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

private val TILE_CENTRE = GeoPoint(46.0511, 14.5051)

/**
 * Which tile `cityTile` is. The name in the resource says it and now the code does too, because the
 * projection needs it: without the coordinate a route in latitude and longitude has nowhere to land.
 */
internal val CITY_TILE: TileCoordinate = TileCoordinate(zoom = 14, x = 8850, y = 5815)

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

@Composable
private fun TripFixture(
    dark: Boolean,
    palette: TilePalette,
    scene: MapScene = tripScene,
) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, CITY_TILE, palette)) {
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
