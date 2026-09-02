@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.driver

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.shashki.auth.SignInConfig
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
                    signIn = signInConfig(),
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

/**
 * The provider, read from the page like everything else about a deployment (B-52).
 *
 * **The same client id as the rider's**, because the server checks `azp` against one value and there
 * is one realm with one kind of user: which bundle somebody opened is which role they are, until the
 * item that gives a driver a role of their own exists. The redirect URI is this bundle's own origin
 * plus `/callback` — computed, because a value typed into two places disagrees with itself.
 */
private fun signInConfig(): SignInConfig? {
    val issuer = oidcIssuer().takeIf { it.isNotBlank() } ?: return null
    return SignInConfig(
        issuer = issuer,
        realm = oidcRealm(),
        clientId = oidcClient(),
        redirectUri = origin() + DriverRoute.BASE + DriverRoute.Callback.path,
    )
}

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.oidcIssuer) || ''")
private external fun oidcIssuer(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.oidcRealm) || 'shashki'")
private external fun oidcRealm(): String

@JsFun("() => (globalThis.SHASHKI && globalThis.SHASHKI.oidcClient) || 'rider'")
private external fun oidcClient(): String
