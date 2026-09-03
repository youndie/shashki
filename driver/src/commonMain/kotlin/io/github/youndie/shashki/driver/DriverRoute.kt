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

    /**
     * Where the provider sends the browser back to (B-52), and it draws nothing — the rider's
     * `Callback` for the same reason: the application arrives with a code in the query, exchanges it
     * and goes to the start.
     */
    @Serializable
    public data object Callback : DriverRoute {
        override val path: String get() = "/callback"
    }

    /** Online or off, and the offer when there is one. */
    @Serializable
    public data object Shift : DriverRoute {
        override val path: String get() = "/"
    }

    /** D1: the three documents a driver hands over before they drive (B-47). */
    @Serializable
    public data object Onboarding : DriverRoute {
        override val path: String get() = "/documents"
    }

    /** D6: what the shift has paid so far (B-46). */
    @Serializable
    public data object Earnings : DriverRoute {
        override val path: String get() = "/earnings"
    }

    /**
     * D5: what the trip that just ended paid (B-70). **Replaces the trip on the stack** rather than
     * being pushed over it: the back button from a summary should not offer to drive a finished ride.
     */
    @Serializable
    public data class Summary(
        val rideId: String,
    ) : DriverRoute {
        override val path: String get() = "/summary/$rideId"
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
         * Where this bundle is served: `BUNDLES` in the server's `BundleRouting` puts the driver
         * under a prefix and the rider at the root, because a demo is opened by a rider.
         *
         * **The routes above do not carry it** — a route is a fact about the application — and the
         * address bar is wrapped in it instead.
         */
        public const val BASE: String = "/driver"

        /**
         * The address the browser was opened at, as a route — or `null` for one this application has
         * no screen for, which the caller answers with its own start rather than with a crash.
         */
        public fun ofPath(path: String): DriverRoute? =
            when {
                path == Callback.path -> Callback

                path == Earnings.path -> Earnings

                path == Onboarding.path -> Onboarding

                path == "/" || path.isEmpty() -> Shift

                path.startsWith(TRIP_PREFIX) -> path.removePrefix(TRIP_PREFIX).takeIf { it.isNotBlank() }?.let(::Trip)

                path.startsWith(
                    SUMMARY_PREFIX,
                ) -> path.removePrefix(SUMMARY_PREFIX).takeIf { it.isNotBlank() }?.let(::Summary)

                else -> null
            }

        private const val TRIP_PREFIX = "/trip/"
        private const val SUMMARY_PREFIX = "/summary/"
    }
}
