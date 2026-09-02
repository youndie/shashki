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
import io.github.youndie.shashki.ui.screens.CancelPrompt
import io.github.youndie.shashki.ui.screens.MatchingStage
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import io.github.youndie.shashki.ui.screens.RiderFinished
import io.github.youndie.shashki.ui.screens.RiderMatching
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

/**
 * R5: the wait, with no map on it.
 *
 * **The dots are animated and the golden is a still, which is the honest limit of this image.** What
 * it holds is the layout — a 24/300 headline, the kit's five dots below it, the destination on the
 * baseline and one action in the bar — and not the 4.4-second cycle, which `KvadrantProgressTest` in
 * the kit owns. A golden that tried to pin the animation would be a golden that fails on timing.
 */
@ViddikScreenshot(name = "rider matching", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderMatchingFixture() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderMatching(
            stage = MatchingStage.LOOKING,
            headline = "looking for a car",
            supporting = "asking the drivers around you",
            destination = "airport",
            meta = "22.8 km · 35 min",
            actionLabel = "cancel",
            onAction = {},
        )
    }
}

/**
 * R5·a: the cascade ran out of drivers.
 *
 * The headline is the kit's largest — 54/200, the same slot a fare uses — because this is an answer
 * rather than a status. **There is no *notify me* beside *try again*** and there is deliberately no
 * disabled one either: the subscription and the push it needs do not exist in this product, and a
 * greyed button is a promise (B-43).
 */
@ViddikScreenshot(name = "rider no cars nearby", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderNoCarsNearbyFixture() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderMatching(
            stage = MatchingStage.NO_CARS,
            headline = "no cars nearby",
            supporting = "nobody is driving here right now",
            destination = "airport",
            meta = "22.8 km · 35 min",
            actionLabel = "try again",
            onAction = {},
        )
    }
}

/**
 * R10: the confirmation, with the number on it.
 *
 * **The amount is in the message and that is the whole point of photographing this.** The fee is a
 * quarter of the fare once a driver has set off, the server computes it, and the screen shows what
 * it was given — a confirmation that said "a fee may apply" would pass every test and hide the one
 * fact the rider needs.
 */
@ViddikScreenshot(name = "rider cancel confirm", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderCancelConfirmFixture() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderMatching(
            stage = MatchingStage.LOOKING,
            headline = "looking for a car",
            supporting = "asking the drivers around you",
            destination = "airport",
            meta = "22.8 km · 35 min",
            actionLabel = "cancel",
            onAction = {},
            prompt =
                CancelPrompt(
                    title = "cancel the ride?",
                    message = "a driver is on the way, so cancelling now costs $ 7.24.",
                    confirmLabel = "cancel the ride",
                    dismissLabel = "keep waiting",
                ),
        )
    }
}

/**
 * R8: what the ride cost, how it was, and whether to add anything (B-44).
 *
 * **The sum is the settlement's capture and not the quote**, which is the same number for a fare and
 * a quarter of it for a cancellation — a screen showing the quote would be right until the first
 * ride that ended early. Three stars are filled because a still has to choose one; the interaction
 * is that tapping the third means three.
 *
 * *skip* sits in the tip row at the size of the other buttons: the common answer is no tip, and a
 * product whose refusal is harder to find than its consent is doing something else.
 */
@ViddikScreenshot(name = "rider finished", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderFinishedFixture() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderFinished(
            total = "$ 28.96",
            destination = "airport · 22.8 km",
            driver = "driver-1",
            stars = 3,
            tips = listOf("$ 2", "$ 5", "$ 10"),
            selectedTip = 1,
            doneLabel = "done",
            onStars = {},
            onTip = {},
            onDone = {},
        )
    }
}

private val LJUBLJANA = GeoPoint(46.0511, 14.5051)
private val BRNIK = GeoPoint(46.2237, 14.4576)
