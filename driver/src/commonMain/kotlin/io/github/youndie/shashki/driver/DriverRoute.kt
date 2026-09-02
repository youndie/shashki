package io.github.youndie.shashki.driver

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Where the driver can be, as a closed set.
 *
 * **The offer is not one of them, and that is a decision rather than an omission.** An address is
 * something a person can bookmark, paste and come back to; an offer lives for fifteen seconds and
 * then belongs to somebody else, so `/offer/abc` would be a link that is broken by design. It is a
 * state of the shift screen — which is also how the driver experiences it: they were waiting, and
 * now there is a card.
 *
 * `@Serializable` and a sealed root for the same two platform reasons as the rider's: Navigation 3
 * restores its stack by serialising keys, and outside the JVM there is no reflection to do it with.
 */
@Serializable
public sealed interface DriverRoute : NavKey {
    /** The address this route occupies. **The browser's address bar is part of the interface.** */
    public val path: String

    /** Online or off, and the offer when there is one. */
    @Serializable
    public data object Shift : DriverRoute {
        override val path: String get() = "/"
    }

    /** What the driver accepted. */
    @Serializable
    public data class Trip(
        val rideId: String,
    ) : DriverRoute {
        override val path: String get() = "/trip/$rideId"
    }

    public companion object {
        /**
         * The address the browser was opened at, as a route — or `null` for one this application has
         * no screen for, which the caller answers with its own start rather than with a crash.
         */
        public fun ofPath(path: String): DriverRoute? =
            when {
                path == "/" || path.isEmpty() -> Shift
                path.startsWith(TRIP_PREFIX) -> path.removePrefix(TRIP_PREFIX).takeIf { it.isNotBlank() }?.let(::Trip)
                else -> null
            }

        private const val TRIP_PREFIX = "/trip/"
    }
}
