package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, TilePalette.Dark)) {
            MapPane(emptyScene(MapCamera(TILE_CENTRE)), Modifier.fillMaxSize())
        }
    }
}

@ViddikScreenshot(name = "canvas tile light", group = "map", width = 390, height = 390)
@Composable
internal fun CanvasTileLight() {
    val latin = kvadrantLatin()
    RiderTheme(dark = false, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, TilePalette.Light)) {
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
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(cityTile, TilePalette.Dark)) {
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
