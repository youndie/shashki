package io.github.youndie.shashki.rider

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Where the rider can be, as a closed set.
 *
 * **`@Serializable` and a sealed root, both for reasons the platform imposes.** Navigation 3 restores
 * its back stack by serialising the keys, and outside the JVM there is no reflection to do it with;
 * one sealed root means the polymorphic serializer is registered once rather than per screen.
 */
@Serializable
public sealed interface RiderRoute : NavKey {
    /** The address this route occupies. **The browser's address bar is part of the interface.** */
    public val path: String

    /**
     * Where the provider sends the browser back to.
     *
     * **A route rather than a screen**, and it draws nothing: the application arrives here with a
     * code in the query, exchanges it and goes to the start. What makes it a route at all is that the
     * redirect URI has to be an address the provider will accept and this application will answer —
     * `RiderRoute.ofPath` has to recognise it or the page after a sign-in is a shrug.
     */
    @Serializable
    public data object Callback : RiderRoute {
        override val path: String get() = "/callback"
    }

    @Serializable
    public data object ClassPicker : RiderRoute {
        override val path: String get() = "/"
    }

    /** R9: the rider's own pages — trips, profile, and the promo the server owns (B-45). */
    @Serializable
    public data object History : RiderRoute {
        override val path: String get() = "/trips"
    }

    /** The one screen the server owns. See research §2 D11. */
    @Serializable
    public data object Promo : RiderRoute {
        override val path: String get() = "/promo"
    }

    /**
     * The wait for a car (B-43). **An address of its own**, so a reload while the saga is still
     * asking drivers comes back to the wait rather than to an empty picker.
     */
    @Serializable
    public data class Matching(
        val rideId: String,
    ) : RiderRoute {
        override val path: String get() = "/matching/$rideId"
    }

    /** R8: what the ride cost, and the two things a rider can still do about it (B-44). */
    @Serializable
    public data class Finished(
        val rideId: String,
    ) : RiderRoute {
        override val path: String get() = "/finished/$rideId"
    }

    @Serializable
    public data class Trip(
        val rideId: String,
    ) : RiderRoute {
        override val path: String get() = "/trip/$rideId"
    }

    public companion object {
        /**
         * The address the browser was opened at, as a route — or `null` for one this application has
         * no screen for, which the caller answers with its own start rather than with a crash.
         *
         * **Written out rather than derived from the serializer**, because a URL is a contract with
         * whoever pasted it: `/trip/abc` has to keep meaning the same screen after a refactor that
         * renames the class.
         */
        public fun ofPath(path: String): RiderRoute? =
            when {
                path == "/" || path.isEmpty() -> {
                    ClassPicker
                }

                path == Callback.path -> {
                    Callback
                }

                path == Promo.path -> {
                    Promo
                }

                path.startsWith(TRIP_PREFIX) -> {
                    path.removePrefix(TRIP_PREFIX).takeIf { it.isNotBlank() }?.let(::Trip)
                }

                path.startsWith(FINISHED_PREFIX) -> {
                    path.removePrefix(FINISHED_PREFIX).takeIf { it.isNotBlank() }?.let(::Finished)
                }

                path.startsWith(MATCHING_PREFIX) -> {
                    path.removePrefix(MATCHING_PREFIX).takeIf { it.isNotBlank() }?.let(::Matching)
                }

                else -> {
                    null
                }
            }

        private const val TRIP_PREFIX = "/trip/"
        private const val MATCHING_PREFIX = "/matching/"
        private const val FINISHED_PREFIX = "/finished/"
    }
}
