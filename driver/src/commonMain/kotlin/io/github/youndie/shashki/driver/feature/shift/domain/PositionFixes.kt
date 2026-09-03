package io.github.youndie.shashki.driver.feature.shift.domain

import io.github.youndie.shashki.protocol.GeoPoint
import kotlinx.coroutines.flow.Flow

/** Where the position on the wire came from (B-49). */
public enum class PositionSource {
    /** The point this bundle was configured with. A parked driver, honestly. */
    CONFIGURED,

    /** The device said so — `navigator.geolocation`, with a person's permission. */
    DEVICE,
}

/** One position and the answer to "says who". */
public data class Fix(
    val at: GeoPoint,
    val source: PositionSource,
)

/**
 * The stream of fixes a shift sends (B-49).
 *
 * **The socket does not know which it is being fed**, which is the whole point of the port: a device
 * fix and a configured point are the same `DriverReport` on the wire, and the server indexes both
 * the same way. What differs is what the *screen* is allowed to claim, and that is what [Fix] carries
 * back.
 *
 * A fix is always available. The first one is the configured point, emitted at once, because a driver
 * who pressed "go online" and waits for a permission prompt to be answered before becoming a
 * candidate has been told a small lie by the button — and a permission that is never granted would
 * make that lie permanent.
 */
public fun interface PositionFixes {
    public fun fixes(configured: GeoPoint): Flow<Fix>
}
