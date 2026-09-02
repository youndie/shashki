package io.github.youndie.shashki.driver.feature.shift.data

import io.github.youndie.shashki.driver.feature.shift.domain.Fix
import io.github.youndie.shashki.driver.feature.shift.domain.PositionFixes
import io.github.youndie.shashki.driver.feature.shift.domain.PositionSource
import io.github.youndie.shashki.protocol.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The configured point first, and the device's own position after it if there is one (B-49).
 *
 * **Nothing here fabricates movement.** [deviceLocation] emits only what the platform produced: on
 * the web that is `navigator.geolocation.watchPosition` after a person granted permission, and
 * everywhere else it is a flow that emits nothing at all. A denied permission, a browser with no
 * geolocation, and a desktop window are the same case — no emission — and the fallback is the same
 * configured point the bundle has always sent. B-29 refused a fake walk along a route and the reason
 * has not changed: a drift this client invents is data the server indexes as fact.
 */
public class DevicePositionFixes(
    private val device: () -> Flow<GeoPoint> = ::deviceLocation,
) : PositionFixes {
    override fun fixes(configured: GeoPoint): Flow<Fix> =
        flow {
            emit(Fix(configured, PositionSource.CONFIGURED))
            emitAll(device().map { Fix(it, PositionSource.DEVICE) })
        }
}

/**
 * The platform's own positions, or nothing.
 *
 * Returning a flow that never emits is not a stub — it is the honest answer on a target where there
 * is nobody to ask, and it is the same answer a denied permission gives.
 */
public expect fun deviceLocation(): Flow<GeoPoint>
