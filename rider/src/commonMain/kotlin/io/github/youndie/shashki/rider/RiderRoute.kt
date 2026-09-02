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

    /** The one screen the server owns. See research §2 D11. */
    @Serializable
    public data object Promo : RiderRoute {
        override val path: String get() = "/promo"
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
                path == "/" || path.isEmpty() -> ClassPicker
                path == Callback.path -> Callback
                path == Promo.path -> Promo
                path.startsWith(TRIP_PREFIX) -> path.removePrefix(TRIP_PREFIX).takeIf { it.isNotBlank() }?.let(::Trip)
                else -> null
            }

        private const val TRIP_PREFIX = "/trip/"
    }
}
