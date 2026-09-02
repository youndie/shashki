package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.ui.kompot.EarningsTile
import io.github.youndie.shashki.ui.kompot.TripRow
import io.github.youndie.shashki.ui.map.LocalMapSurface
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.PlaceholderMapSurface
import io.github.youndie.shashki.ui.screens.CancelPrompt
import io.github.youndie.shashki.ui.screens.DriverEarnings
import io.github.youndie.shashki.ui.screens.DriverOnboarding
import io.github.youndie.shashki.ui.screens.MatchingStage
import io.github.youndie.shashki.ui.screens.OnboardingDocument
import io.github.youndie.shashki.ui.screens.OnboardingState
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import io.github.youndie.shashki.ui.screens.RiderFinished
import io.github.youndie.shashki.ui.screens.RiderHistory
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
internal fun RiderClassPickerFixture(): Unit = RiderClassPicker(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider class picker light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderClassPickerFixtureLight(): Unit = RiderClassPicker(dark = false)

@Composable
private fun RiderClassPicker(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
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
internal fun RiderMatchingFixture(): Unit = RiderMatching(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider matching light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderMatchingFixtureLight(): Unit = RiderMatching(dark = false)

@Composable
private fun RiderMatching(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
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
internal fun RiderNoCarsNearbyFixture(): Unit = RiderNoCarsNearby(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider no cars nearby light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderNoCarsNearbyFixtureLight(): Unit = RiderNoCarsNearby(dark = false)

@Composable
private fun RiderNoCarsNearby(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
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
internal fun RiderCancelConfirmFixture(): Unit = RiderCancelConfirm(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider cancel confirm light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderCancelConfirmFixtureLight(): Unit = RiderCancelConfirm(dark = false)

@Composable
private fun RiderCancelConfirm(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
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
internal fun RiderFinishedFixture(): Unit = RiderFinished(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider finished light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderFinishedFixtureLight(): Unit = RiderFinished(dark = false)

@Composable
private fun RiderFinished(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
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
            // The three facts B-59 added, in the state a rider is in a moment after choosing a tip:
            // the card and the duration, and the sum with the tip on it. `skipped` stays false —
            // somebody has chosen, and it was not the refusal.
            meta = "paid with card-4417 · 35 min",
            totalWithTip = "$ 33.96",
        )
    }
}

/**
 * R9: the rider's own pages, with the trips list on the first (B-45).
 *
 * **The rows are the kompot renderer, drawn natively.** `TripRow` stays a registered component and
 * this screen calls its renderer directly — the property D11 argues for, rather than a contradiction
 * of it: a list of somebody's own rides has an obvious native version, and the server keeps the
 * screen that does not.
 *
 * The fare on each row is what the settlement took, which is why the cancelled ride shows a quarter
 * of its journey and the one nobody drove shows nothing at all.
 */
@ViddikScreenshot(name = "rider history", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderHistoryFixture(): Unit = RiderHistory(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider history light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderHistoryFixtureLight(): Unit = RiderHistory(dark = false)

@Composable
private fun RiderHistory(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderHistory(
            titles = listOf("trips", "profile", "promo"),
            trips =
                listOf(
                    TripRow("ride-3", "airport", "today · 22.8 km", "$ 28.96", accent = true),
                    TripRow("ride-2", "airport", "yesterday · cancelled", "$ 7.24"),
                    TripRow("ride-1", "airport", "monday · no cars nearby", "—"),
                ),
            emptyLine = "no trips yet",
            profile = listOf("name" to "rider-1", "email" to "rider@example.com"),
            onTrip = {},
        )
    }
}

/**
 * The same pivot with nothing in it: **the kit's section 08 empty list** — one line in the disabled
 * brush and no action. A button here would invite the rider to fix something they have not done
 * wrong; they have simply not taken a ride yet.
 */
@ViddikScreenshot(name = "rider history empty", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderHistoryEmptyFixture(): Unit = RiderHistoryEmpty(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider history empty light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderHistoryEmptyFixtureLight(): Unit = RiderHistoryEmpty(dark = false)

@Composable
private fun RiderHistoryEmpty(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderHistory(
            titles = listOf("trips", "profile", "promo"),
            trips = emptyList(),
            emptyLine = "no trips yet",
            profile = listOf("name" to "rider-1", "email" to "rider@example.com"),
            onTrip = {},
        )
    }
}

/**
 * D6: today, this week, and everything — from the payout rows (B-46).
 *
 * **One `54` on the screen and it is today's sum**, drawn as the page's own figure; the tiles below
 * carry `32`s through `EarningsTileRenderer`, which is the kit's section 08 rule 3. A grid of `54`s
 * would be four page titles.
 *
 * The digits are tabular — `ShashkiTypography` makes both figures `tnum` — so a number that changes
 * while a driver watches it does not shuffle its neighbours.
 */
@ViddikScreenshot(name = "driver earnings", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverEarningsFixture(): Unit = DriverEarnings(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "driver earnings light", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverEarningsFixtureLight(): Unit = DriverEarnings(dark = false)

@Composable
private fun DriverEarnings(dark: Boolean) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        DriverEarnings(
            titles = listOf("today", "week", "history"),
            today = "$ 23.16",
            todayLabel = "today, 1 ride",
            tiles =
                listOf(
                    // **Three, because three is what the client builds.** This fixture had two, so
                    // the golden of this screen was a picture of a screen nobody ships: the third
                    // tile is what pushed the row past the grid's four columns.
                    EarningsTile("today", "today", "$ 23.16", size = 2, accent = true),
                    EarningsTile("week", "week", "$ 118.40", size = 2),
                    EarningsTile("all", "all time", "$ 1 204.60", size = 2),
                ),
            history = listOf("airport · today" to "$ 23.16"),
            emptyLine = "nothing yet",
        )
    }
}

/**
 * D1: the three documents, in all three states (B-47).
 *
 * **The third state is drawn and not produced**, which is the honest half of this picture: nothing
 * in this product accepts a document, because accepting is a person and a queue. Drawing it is what
 * makes the day a reviewer exists a change to the server rather than to the screen.
 *
 * The colours are the kit's semantics — the inactive brush for missing, the driver's amber for
 * pending, green for accepted — and the upload field is white in both themes, which is one of the
 * two places the kit says fight that instinct.
 */
@ViddikScreenshot(name = "driver onboarding", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverOnboardingFixture(): Unit = DriverOnboardingBody(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "driver onboarding light", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverOnboardingFixtureLight(): Unit = DriverOnboardingBody(dark = false)

@Composable
private fun DriverOnboardingBody(dark: Boolean) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        DriverOnboarding(
            documents =
                listOf(
                    OnboardingDocument("driving licence", "sent 2 minutes ago · 1.2 MB", OnboardingState.PENDING),
                    OnboardingDocument("insurance", "checked yesterday", OnboardingState.ACCEPTED),
                    OnboardingDocument("photo of the car", "not sent yet", OnboardingState.MISSING),
                ),
            uploadLabel = "choose a file",
            note = "three documents, and a person looks at them",
            onUpload = {},
        )
    }
}

private val LJUBLJANA = GeoPoint(46.0511, 14.5051)
private val BRNIK = GeoPoint(46.2237, 14.4576)
