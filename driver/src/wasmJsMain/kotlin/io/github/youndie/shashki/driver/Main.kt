@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.driver

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The driver bundle's entry point.
 *
 * **The same page contract as the rider's**, read from `globalThis.SHASHKI` rather than compiled in:
 * D10's two bundles are two artefacts, and an artefact that carried its deployment inside it would
 * stop being content-addressable.
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ComposeViewport(document.body!!) {
        DriverApp(
            config =
                DriverConfig(
                    serverUrl = origin(),
                    driverId = driverId(),
                    rideClass = RideClass.ECONOMY,
                    rating = DEFAULT_RATING,
                    at = DriverConfig.LJUBLJANA_CENTRE,
                    katcherUrl = katcherUrl().takeIf { it.isNotBlank() },
                    katcherAppKey = katcherAppKey().takeIf { it.isNotBlank() },
                    release = release(),
                ),
            scope = scope,
        )
    }
}

private const val DEFAULT_RATING = 4.9

@JsFun("() => window.location.origin")
private external fun origin(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.driverId) || 'driver-1'")
private external fun driverId(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.katcherUrl) || ''")
private external fun katcherUrl(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.katcherAppKey) || ''")
private external fun katcherAppKey(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.release) || 'dev'")
private external fun release(): String
