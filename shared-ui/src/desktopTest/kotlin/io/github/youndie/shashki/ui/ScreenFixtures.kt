package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.protocol.FareBreakdown
import io.github.youndie.shashki.protocol.FareLine
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.protocol.TripRow
import io.github.youndie.shashki.ui.map.LocalMapSurface
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.PlaceholderMapSurface
import io.github.youndie.shashki.ui.screens.CancelPrompt
import io.github.youndie.shashki.ui.screens.DriverEarnings
import io.github.youndie.shashki.ui.screens.DriverOnboarding
import io.github.youndie.shashki.ui.screens.DriverTripSummary
import io.github.youndie.shashki.ui.screens.DriverTripSummaryState
import io.github.youndie.shashki.ui.screens.MatchingStage
import io.github.youndie.shashki.ui.screens.OnboardingDocument
import io.github.youndie.shashki.ui.screens.OnboardingState
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import io.github.youndie.shashki.ui.screens.RiderFinished
import io.github.youndie.shashki.ui.screens.RiderHistory
import io.github.youndie.shashki.ui.screens.RiderMatching
import io.github.youndie.shashki.ui.screens.RiderReceipt
import io.github.youndie.shashki.ui.screens.TripMonth
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
                // The router's answer for Slovenska cesta 15 — Brnik terminal B, not the kit's
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
 * R4 with nobody driving anything (B-62).
 *
 * **This is the state a stand is in most of the time and no golden had it.** Every class unavailable,
 * every price an em dash, and the bar saying so rather than quoting a figure for a ride that cannot
 * be ordered — which is what it did until B-62: `no cars nearby · $ 28.96` on all three rows, and
 * `order · $ 28.96` underneath.
 */
@ViddikScreenshot(name = "rider class picker empty", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderClassPickerEmptyFixture(): Unit = RiderClassPickerEmpty(dark = true)

/** The same on the stock light theme (B-48). */
@ViddikScreenshot(name = "rider class picker empty light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderClassPickerEmptyFixtureLight(): Unit = RiderClassPickerEmpty(dark = false)

@Composable
private fun RiderClassPickerEmpty(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        // The screen draws a map and a golden has no archive: the placeholder is what every other
        // map fixture here uses, for the reason B-01 gives.
        CompositionLocalProvider(LocalMapSurface provides PlaceholderMapSurface()) {
            RiderClassPicker(
                scene =
                    MapScene(
                        camera = MapCamera(LJUBLJANA, zoom = 14.0),
                        pins =
                            listOf(MapPin(LJUBLJANA, MapPin.Kind.PICKUP), MapPin(BRNIK, MapPin.Kind.DROPOFF)),
                    ),
                destination = "airport",
                destinationMeta = "26.3 km · 20 min",
                offers =
                    listOf(
                        RideClassOffer("economy", "no cars nearby", null, carRects = 1, available = false),
                        RideClassOffer("comfort", "no cars nearby", null, carRects = 2, available = false),
                        RideClassOffer("business", "no cars nearby", null, carRects = 3, available = false),
                    ),
                selectedIndex = 0,
                paymentLabel = "card ·· 4417",
                orderLabel = "no cars nearby",
                canOrder = false,
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
            // The kit's R5, line by line: how many, which one and for how long, and what for (B-73).
            supporting = "3 cars nearby",
            progress = "asking the closest first · 0:12",
            order = "economy · $ 28.96",
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
            supporting = "3 cars nearby",
            progress = "asking the closest first · 0:12",
            order = "economy · $ 28.96",
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
            // **Two months, and rows that say where and when** (B-61). This fixture had three rows
            // all titled "airport" — the destination this demo always orders — which is a list where
            // every line is identical, and a golden of a screen nobody can read.
            months =
                listOf(
                    TripMonth(
                        "september 2026",
                        listOf(
                            TripRow(
                                "ride-3",
                                "46.0511, 14.5051 — 46.2237, 14.4576",
                                "2 september · 19:40 · economy · card-4417",
                                "$ 28.96",
                                accent = true,
                            ),
                            TripRow(
                                "ride-2",
                                "46.0511, 14.5051 — 46.0620, 14.5350",
                                "1 september · 08:12 · economy · cancelled",
                                "$ 7.24",
                            ),
                        ),
                    ),
                    TripMonth(
                        "august 2026",
                        listOf(
                            TripRow(
                                "ride-1",
                                "46.0620, 14.5350 — 46.0511, 14.5051",
                                "28 august · 12:05 · comfort · cancelled",
                                "—",
                            ),
                        ),
                    ),
                ),
            emptyLine = "no trips yet",
            profile = listOf("name" to "rider-1", "email" to "rider@example.com"),
            onTrip = {},
        )
    }
}

/**
 * R9·b: the receipt, and the first screen in this product a **server** composed out of **this
 * product's own** components (B-61, B-65).
 *
 * **The tree here is the one the server sends, written out.** It is a fixture and not a fetch, so
 * what this golden proves is the drawing rather than the wiring — `SettlementTest` is where the
 * server's own arithmetic is checked against the money the gateway moved, and `ReceiptTreeTest` is
 * where the wire format is. What is worth photographing is the card: the figure at 54 because the
 * server marked it `primary`, every line under it capped at 19 by the kit's rule, and the labels in
 * the subtle brush against values in the foreground.
 */
@ViddikScreenshot(name = "rider receipt", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderReceiptFixture(): Unit = RiderReceipt(dark = true)

/** The same screen on the stock light theme — open question 1's promise, kept (B-48). */
@ViddikScreenshot(name = "rider receipt light", group = "screens", width = 390, height = 844)
@Composable
internal fun RiderReceiptFixtureLight(): Unit = RiderReceipt(dark = false)

@Composable
private fun RiderReceipt(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        RiderReceipt(
            tree =
                ColumnComponent(
                    id = "receipt",
                    spacing = 16,
                    children =
                        listOf(
                            TextComponent(
                                id = "receipt-title",
                                text = "receipt",
                                style = TypographyToken(ShashkiTokens.TYPE_PAGE_TITLE),
                            ),
                            TextComponent(
                                id = "receipt-ride",
                                text = "ride-3",
                                style = TypographyToken(ShashkiTokens.TYPE_META),
                                color = ColorToken(ShashkiTokens.COLOR_SUBTLE),
                            ),
                            FareBreakdown(
                                id = "receipt-ride-3",
                                amount = "$ 31.96",
                                caption = "economy · 26.3 km · 20 min",
                                primary = true,
                                lines =
                                    listOf(
                                        FareLine("fare", "$ 28.96"),
                                        FareLine("tip", "$ 3"),
                                        FareLine("paid with", "card-4417"),
                                    ),
                            ),
                        ),
                ),
            loading = false,
            onBack = {},
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
            months = emptyList(),
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

/**
 * The same screen in a window (B-67).
 *
 * **A browser has a back button and a window has nothing**, so the application asks the platform and
 * draws the kit's bar only where the answer is no. This is the picture of that answer: one ring
 * button in 54 dp of chrome, and the reason it is photographed rather than assumed is that a control
 * added for one platform is exactly the kind that is drawn on both by accident.
 */
@ViddikScreenshot(name = "driver onboarding in a window", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverOnboardingWindowedFixture(): Unit = DriverOnboardingWindowed(dark = true)

@Composable
private fun DriverOnboardingWindowed(dark: Boolean) {
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
            note = "three documents, and nothing here reviews them yet",
            onUpload = {},
            onBack = {},
        )
    }
}

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
            // **The sentence the product actually passes** (B-60). This said "a person looks at
            // them", which is the opposite of what D1 is honest about: nothing here reviews a
            // document, and a golden that showed a friendlier line was a picture of a screen nobody
            // ships — the same gap the earnings fixture had with two tiles against the client's
            // three.
            note = "three documents, and nothing here reviews them yet",
            onUpload = {},
        )
    }
}

private val LJUBLJANA = GeoPoint(46.0511, 14.5051)
private val BRNIK = GeoPoint(46.2237, 14.4576)

/**
 * D5: the trip that just ended, from the driver's side (B-70).
 *
 * **The figure is what he earned and the fee is a line** — the kit's sentence under this screen,
 * and the reason the fixture's numbers are the settlement's shape: a fare, twenty per cent off it,
 * the tip on top. The accent is on the figure; the bar below is chrome, which is the kit's rule 1
 * the other way round from D3.
 */
@ViddikScreenshot(name = "driver trip summary", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverTripSummaryFixture(): Unit = DriverTripSummary(dark = true)

/** The same on the stock light theme (B-48). */
@ViddikScreenshot(name = "driver trip summary light", group = "screens", width = 390, height = 844)
@Composable
internal fun DriverTripSummaryFixtureLight(): Unit = DriverTripSummary(dark = false)

@Composable
private fun DriverTripSummary(dark: Boolean) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        DriverTripSummary(
            state =
                DriverTripSummaryState(
                    earned = "+$ 26.17",
                    meta = "card-4417 · today $ 46.32",
                    lines =
                        listOf(
                            "fare" to "$ 28.96",
                            "service fee 20 %" to "-$ 5.79",
                            "tip" to "$ 3",
                            "35 min · 22.8 km" to "—",
                        ),
                ),
            onBackToShift = {},
        )
    }
}
