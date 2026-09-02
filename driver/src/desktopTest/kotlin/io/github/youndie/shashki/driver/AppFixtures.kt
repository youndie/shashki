package io.github.youndie.shashki.driver

import androidx.compose.runtime.Composable
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.driver.feature.shift.domain.PositionSource
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftContent
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftUiState
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripContent
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripUiState
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.ui.DriverTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * The driver's own screens, photographed without a graph, a socket or a server.
 *
 * **What these show that `:shared-ui`'s `components_offer_card` cannot is the shell around it** —
 * the shift the card interrupts, and the mapping into it: a `Quote` of 2 490 cents as `$ 24.90`, a
 * `GeoPoint` as four decimals because nothing has geocoded it, and a pickup meta that is a dash
 * because the server answers the rider's leg and not the driver's.
 */
@ViddikScreenshot(name = "shift offline", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftOffline(): Unit = ShiftOfflineBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "shift offline light", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftOfflineLight(): Unit = ShiftOfflineBody(dark = false)

@Composable
private fun ShiftOfflineBody(dark: Boolean) {
    Fixture(dark) {
        ShiftContent(uiState = ShiftUiState(driverLabel = "driver-1"), onAction = { })
    }
}

/**
 * The state a driver is in for most of a shift.
 *
 * **The count is the point of this golden.** "waiting" alone is a word an application can print
 * while its socket is dead; a number that rose to 42 is the socket having taken 42 positions.
 */
@ViddikScreenshot(name = "shift waiting", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftWaiting(): Unit = ShiftWaitingBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "shift waiting light", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftWaitingLight(): Unit = ShiftWaitingBody(dark = false)

@Composable
private fun ShiftWaitingBody(dark: Boolean) {
    Fixture(dark) {
        ShiftContent(
            uiState = ShiftUiState(driverLabel = "driver-1", online = true, reported = 42),
            onAction = { },
        )
    }
}

/**
 * The same shift on a phone that granted the permission (B-49).
 *
 * **This is the only place the difference is visible**, which is why it is photographed: the reports
 * on the wire are identical either way, and a driver looking at "waiting · 42 positions sent" cannot
 * otherwise tell a moving car from a parked one. The line is the whole feature.
 */
@ViddikScreenshot(name = "shift on a device", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftOnDevice(): Unit = ShiftOnDeviceBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "shift on a device light", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftOnDeviceLight(): Unit = ShiftOnDeviceBody(dark = false)

@Composable
private fun ShiftOnDeviceBody(dark: Boolean) {
    Fixture(dark) {
        ShiftContent(
            uiState =
                ShiftUiState(
                    driverLabel = "driver-1",
                    online = true,
                    reported = 42,
                    positionSource = PositionSource.DEVICE,
                ),
            onAction = { },
        )
    }
}

/**
 * Fifteen seconds to decide, with four of them gone.
 *
 * The bar is at 11/15 and the fare and the countdown are both at 54 — the two-second read the kit
 * specifies. `secondsTotal` is what was left when this client first saw the offer, which is why the
 * denominator is fifteen rather than the server's own budget.
 */
@ViddikScreenshot(name = "shift with an offer", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftWithAnOffer(): Unit = ShiftWithAnOfferBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "shift with an offer light", group = "driver", width = 390, height = 844)
@Composable
internal fun ShiftWithAnOfferLight(): Unit = ShiftWithAnOfferBody(dark = false)

@Composable
private fun ShiftWithAnOfferBody(dark: Boolean) {
    Fixture(dark) {
        ShiftContent(
            uiState =
                ShiftUiState(
                    driverLabel = "driver-1",
                    online = true,
                    reported = 42,
                    offer = OFFER,
                    secondsLeft = 11,
                    secondsTotal = 15,
                ),
            onAction = { },
        )
    }
}

/**
 * What was accepted, and the one thing to do about it.
 *
 * **The button is the golden's subject** (B-37): B-29 photographed this screen with nothing to press
 * because the server had no route for the trip's transitions. The state here is `ASSIGNED`, so the
 * action is "on my way" — and the last one, pressed at the end of the trip, is what takes the money.
 */
@ViddikScreenshot(name = "assigned ride", group = "driver", width = 390, height = 844)
@Composable
internal fun AssignedRide(): Unit = AssignedRideBody(dark = true)

/** The same, on the stock light theme (B-48). */
@ViddikScreenshot(name = "assigned ride light", group = "driver", width = 390, height = 844)
@Composable
internal fun AssignedRideLight(): Unit = AssignedRideBody(dark = false)

@Composable
private fun AssignedRideBody(dark: Boolean) {
    Fixture(dark) {
        DriverTripContent(
            uiState =
                DriverTripUiState(
                    RideView(
                        id = "ride-1",
                        status = RideStatus.ASSIGNED,
                        rideClass = RideClass.ECONOMY,
                        pickup = PICKUP,
                        dropoff = DROPOFF,
                        quote = QUOTE,
                        driverId = "driver-1",
                    ),
                ),
            onAction = { },
        )
    }
}

@Composable
private fun Fixture(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val latin = kvadrantLatin()
    DriverTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        content()
    }
}

private val PICKUP = GeoPoint(46.0511, 14.5051)
private val DROPOFF = GeoPoint(46.2237, 14.4576)
private val QUOTE = Quote(22_806, 2_079, 2_490, "USD")

private val OFFER =
    OfferView(
        rideId = "ride-1",
        rideClass = RideClass.ECONOMY,
        quote = QUOTE,
        pickup = PICKUP,
        dropoff = DROPOFF,
        expiresAtEpochMs = 1_000_015_000,
        nowEpochMs = 1_000_000_000,
    )
