package io.github.youndie.shashki.rider

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The same application in a window, at the kit's canvas.
 *
 * **It exists so the browser build can be looked at without a browser.** There is none on the build
 * box, viddik photographs JVM targets only, and a screen nobody can see is a screen nobody can
 * review. The address bar is [NoAddressBar] here and says so.
 */
public fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "shashki · rider",
            state = rememberWindowState(size = DpSize(390.dp, 844.dp)),
        ) {
            RiderApp(
                config =
                    RiderConfig(
                        serverUrl = System.getenv("SHASHKI_SERVER") ?: "http://127.0.0.1:8080",
                        riderId = "rider-1",
                        paymentMethodId = "card-4417",
                        // No provider: a window has no redirect to come back from, and the
                        // desktop build exists to be photographed rather than signed into.
                        signIn = null,
                        tilesUrl = System.getenv("SHASHKI_TILES"),
                        katcherUrl = System.getenv("SHASHKI_KATCHER_URL"),
                        katcherAppKey = System.getenv("SHASHKI_KATCHER_KEY"),
                        release = System.getenv("SHASHKI_RELEASE") ?: "dev",
                    ),
                scope = scope,
            )
        }
    }
}
