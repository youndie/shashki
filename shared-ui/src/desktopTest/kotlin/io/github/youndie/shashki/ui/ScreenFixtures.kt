package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.ui.map.LocalMapSurface
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.PlaceholderMapSurface
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * R4 with a map-shaped hole where the renderer will go.
 *
 * **The hole is the point.** B-01 has not chosen a route, so there is nothing to draw the basemap
 * with — and a screen that hid that fact would be a screen nobody could tell apart from one whose
 * map failed to load. The placeholder paints the style's own background colour and says what is
 * missing, so this golden is honest about being 360 dp short of a screen. When a renderer is bound
 * the image changes, and that change is the evidence.
 *
 * Everything below the map is finished: the destination at 32/200, three `ClassTile`s with one
 * accent surface, the payment row, and the order bar at the app bar's height.
 */
@ViddikScreenshot(name = "rider class picker", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderClassPickerFixture() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        CompositionLocalProvider(LocalMapSurface provides PlaceholderMapSurface()) {
            RiderClassPicker(
                scene =
                    MapScene(
                        camera = MapCamera(LJUBLJANA, zoom = 14.0),
                        pins =
                            listOf(
                                MapPin(LJUBLJANA, MapPin.Kind.PICKUP),
                                MapPin(BRNIK, MapPin.Kind.DROPOFF),
                            ),
                    ),
                destination = "airport",
                // The router's answer for Slovenska cesta 15 → Brnik terminal B, not the kit's
                // guess — B-06 measured it on the graph it imports.
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

private val LJUBLJANA = GeoPoint(46.0511, 14.5051)
private val BRNIK = GeoPoint(46.2237, 14.4576)
