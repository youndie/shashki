package io.github.youndie.shashki.driver

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The same application in a window, at the kit's canvas.
 *
 * It exists so the browser build can be looked at without a browser, and — for this bundle
 * particularly — so the socket can be driven against a local server from a machine that has one.
 */
public fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "shashki · driver",
            state = rememberWindowState(size = DpSize(390.dp, 844.dp)),
        ) {
            DriverApp(
                config =
                    DriverConfig(
                        serverUrl = System.getenv("SHASHKI_SERVER") ?: "http://127.0.0.1:8080",
                        driverId = System.getenv("SHASHKI_DRIVER") ?: "driver-1",
                        rideClass = RideClass.ECONOMY,
                        rating = DEFAULT_RATING,
                        at = DriverConfig.LJUBLJANA_CENTRE,
                        katcherUrl = System.getenv("SHASHKI_KATCHER_URL"),
                        katcherAppKey = System.getenv("SHASHKI_KATCHER_KEY"),
                        release = System.getenv("SHASHKI_RELEASE") ?: "dev",
                        // No provider: a window has no redirect to come back from, and a server
                        // with one refuses every driver route this build makes (B-52). The desktop
                        // driver is for looking at screens and for driving a local stand.
                        signIn = null,
                    ),
                scope = scope,
            )
        }
    }
}

private const val DEFAULT_RATING = 4.9
