package io.github.youndie.shashki.rider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerContent
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerUiState
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.map.CanvasMapSurface
import io.github.youndie.shashki.ui.map.LocalMapSurface
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * The application's own screens, photographed without a graph and without a server.
 *
 * **This is what the Screen/Content split buys.** `ClassPickerScreen` resolves a view model out of
 * Koin and collects its events; `ClassPickerContent` takes a state and a callback. Only the second
 * can be a golden, and it is the half that contains everything the design cares about.
 *
 * What these show that `:shared-ui`'s goldens cannot is the **mapping**: a `Quote` of 3 890 cents
 * arriving as `$ 38.90`, 22 806 metres as `22.8 km`, 2 079 seconds as `35 min`, and a pickup wait of
 * 240 seconds as `4 min`. Those conversions are the application's, they are easy to get subtly
 * wrong, and until now nothing looked at them.
 *
 * The third row is the one to read: `business` has a price and no wait, so it draws as the kit's
 * unavailable tile saying "no cars nearby" (B-31). A class the server prices and nobody is driving
 * is not a class a rider can order.
 */
@ViddikScreenshot(name = "class picker priced", group = "rider", width = 390, height = 844)
@Composable
internal fun ClassPickerPriced(): Unit = ClassPickerPricedBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "class picker priced light", group = "rider", width = 390, height = 844)
@Composable
internal fun ClassPickerPricedLight(): Unit = ClassPickerPricedBody(dark = false)

@Composable
private fun ClassPickerPricedBody(dark: Boolean) {
    Fixture(dark) {
        ClassPickerContent(
            scene = MapScene(camera = MapCamera(CENTRE)),
            uiState =
                ClassPickerUiState(
                    loading = false,
                    quotes =
                        listOf(
                            ClassQuote(RideClass.ECONOMY, Quote(22_806, 2_079, 2_490, "USD"), 240),
                            ClassQuote(RideClass.COMFORT, Quote(22_806, 2_079, 3_890, "USD"), 360),
                        ),
                    selected = RideClass.ECONOMY,
                    distanceMetres = 22_806,
                    durationSeconds = 2_079,
                ),
            onAction = { },
        )
    }
}

/**
 * The state a rider actually meets first, and the one nobody draws on purpose: the server has not
 * answered yet. **Every class is unavailable and the meta is an ellipsis** — which is the screen
 * saying "I do not know" rather than showing prices it does not have.
 */
@ViddikScreenshot(name = "class picker before the server answers", group = "rider", width = 390, height = 844)
@Composable
internal fun ClassPickerLoading(): Unit = ClassPickerLoadingBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "class picker before the server answers light", group = "rider", width = 390, height = 844)
@Composable
internal fun ClassPickerLoadingLight(): Unit = ClassPickerLoadingBody(dark = false)

@Composable
private fun ClassPickerLoadingBody(dark: Boolean) {
    Fixture(dark) {
        ClassPickerContent(
            scene = MapScene(camera = MapCamera(CENTRE)),
            uiState = ClassPickerUiState(),
            onAction = { },
        )
    }
}

/**
 * The theme and the basemap, together (B-48).
 *
 * **The palette moves with the theme and that pairing is the point**: a light screen on the dark
 * basemap is the defect this item would most easily ship, because nothing about the map is part of
 * the theme — the two palettes belong to the styles.
 */
@Composable
private fun Fixture(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        val palette = if (dark) TilePalette.Dark else TilePalette.Light
        CompositionLocalProvider(LocalMapSurface provides CanvasMapSurface(palette = palette)) {
            content()
        }
    }
}

private val CENTRE = GeoPoint(46.0511, 14.5051)
