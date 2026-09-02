@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.driver.feature.shift.data

import io.github.youndie.shashki.protocol.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * `navigator.geolocation.watchPosition`, and nothing else (B-49).
 *
 * **A refusal is silence rather than an error.** A person who declines the prompt, a browser served
 * over plain HTTP where the API is not available, and a device that cannot get a fix all end up in
 * the same place: this flow emits nothing and the shift keeps sending the configured point, which
 * the screen then says it is doing. Turning a denial into a thrown exception would take the shift
 * offline over a permission a driver is entitled to withhold.
 *
 * The watch is cleared when the flow is cancelled — going offline stops the GPS as well as the
 * socket, which is the behaviour a phone in a pocket deserves.
 */
public actual fun deviceLocation(): Flow<GeoPoint> =
    callbackFlow {
        val watch =
            startWatch(
                // `trySend` and not a suspending send: this runs on the browser's callback and a
                // full buffer means the shift is behind, in which case the newest fix is the one
                // worth keeping and the dropped one is a position already left behind.
                onFix = { latitude, longitude -> trySend(GeoPoint(latitude, longitude)) },
                // Deliberately empty: see above. The screen already says the position is configured,
                // which is the report a driver needs and the one this cannot improve on.
                onDenied = { },
            )
        awaitClose { if (watch >= 0) clearWatch(watch) }
    }

/**
 * `-1` when the browser has no geolocation at all, otherwise the watch id.
 *
 * `enableHighAccuracy` because a car needs the GPS rather than the nearest cell; `maximumAge` of five
 * seconds because a shift sends a report every four and a cached fix older than that is a position
 * the driver has already left.
 */
private fun startWatch(
    onFix: (Double, Double) -> Unit,
    onDenied: () -> Unit,
): Int =
    js(
        """{
        if (!navigator.geolocation) { onDenied(); return -1; }
        return navigator.geolocation.watchPosition(
            function (p) { onFix(p.coords.latitude, p.coords.longitude); },
            function () { onDenied(); },
            { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 }
        );
    }""",
    )

private fun clearWatch(id: Int) {
    js("navigator.geolocation.clearWatch(id);")
}
