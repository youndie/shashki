package io.github.youndie.shashki.rider

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.auth.Session
import io.github.youndie.shashki.crash.installCrashReporting
import io.github.youndie.shashki.rider.feature.promo.ui.PromoScreen
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerScreen
import io.github.youndie.shashki.rider.feature.ride.ui.FinishedScreen
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingScreen
import io.github.youndie.shashki.rider.feature.ride.ui.TripScreen
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.map.LocalMapSurface
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.MapSurface
import io.github.youndie.shashki.ui.nav.addressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

/**
 * The rider application: one Koin graph, one back stack, one theme.
 *
 * **This is the shell every other module's work has been waiting for** (B-28). The screens, the map
 * surface, the PKCE client and the crash reporter were all built and tested with nothing to hang
 * them in; here they hang.
 */
@Composable
public fun RiderApp(
    config: RiderConfig,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    KoinApplication(koinConfiguration { modules(riderModule(config, scope)) }) {
        CrashReporting(scope)

        val latin = kvadrantLatin()
        RiderTheme(latin = latin, typography = ShashkiTypography.of(latin)) {
            CompositionLocalProvider(LocalMapSurface provides koinInject<MapSurface>()) {
                RiderNavigation(modifier)
            }
        }
    }
}

/**
 * B-10's hook, called.
 *
 * **Inside the Koin scope and not at `main`**, because the reporter needs the application's own HTTP
 * client — a second one would be a second connection pool for the rarest request the application
 * makes. `null` when no katcher is configured: a demo pointed at nothing should not report to
 * nothing and pretend otherwise.
 */
@Composable
private fun CrashReporting(scope: CoroutineScope) {
    val reporting = koinInject<CrashReporting>()
    DisposableEffect(reporting) {
        reporting.reporter?.let { installCrashReporting(it, scope) }
        onDispose { }
    }
}

/**
 * The back stack, and the address bar kept in step with it.
 *
 * **Both directions, because a browser has both.** Following a link pushes an address; pressing back
 * pops one and forward pushes it again — a handler that only popped would break forward silently.
 * The stack is Navigation 3's, restored across process death by its own serializer, which is what
 * the routes are `@Serializable` for.
 */
@Composable
private fun RiderNavigation(modifier: Modifier = Modifier) {
    val bar = remember { addressBar() }
    val start = remember { RiderRoute.ofPath(bar.openedAt()) ?: RiderRoute.ClassPicker }
    val backStack = rememberNavBackStack(SAVED_STATE, start)

    // Out: the address follows the top of the stack.
    LaunchedEffect(backStack.lastOrNull()) {
        (backStack.lastOrNull() as? RiderRoute)?.let { bar.push(it.path) }
    }

    // In: the browser's buttons move the stack. An address this application has no screen for is
    // ignored rather than crashed on — somebody else's link is not this application's bug.
    DisposableEffect(bar) {
        bar.onNavigate { path ->
            val route = RiderRoute.ofPath(path)
            if (route != null && backStack.lastOrNull() != route) {
                val existing = backStack.indexOfLast { it == route }
                if (existing >= 0) {
                    while (backStack.size > existing + 1) backStack.removeAt(backStack.size - 1)
                } else {
                    backStack.add(route)
                }
            }
        }
        onDispose { }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
        entryProvider =
            entryProvider {
                entry<RiderRoute.ClassPicker> {
                    ClassPickerScreen(
                        scene = MapScene(camera = MapCamera(RiderConfig.LJUBLJANA_CENTRE)),
                        // **To the wait, not to the trip** (B-43). Between the order and a driver
                        // there is a stretch with no car, and the trip screen drawn over it is a
                        // screen with a hole where the driver goes.
                        onOrdered = { rideId -> backStack.add(RiderRoute.Matching(rideId)) },
                        onFailed = { },
                    )
                }
                entry<RiderRoute.Matching> { route ->
                    MatchingScreen(
                        rideId = route.rideId,
                        // **Replacing rather than pushing**: a driver has been found, and the back
                        // button from a trip should not offer to watch the search for it again.
                        onAssigned = { rideId ->
                            if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
                            backStack.add(RiderRoute.Trip(rideId))
                        },
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                        onFailed = { },
                    )
                }
                entry<RiderRoute.Trip> { route ->
                    TripScreen(
                        rideId = route.rideId,
                        // **A finished ride is R8 and not the picker** (B-44). `COMPLETED` used to
                        // pop the trip and leave the rider back where they started, with no sum, no
                        // rating and nowhere to put a tip.
                        onFinished = {
                            if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
                            backStack.add(RiderRoute.Finished(route.rideId))
                        },
                        onFailed = { },
                    )
                }
                entry<RiderRoute.Finished> { route ->
                    FinishedScreen(
                        rideId = route.rideId,
                        // Done means done: back to the picker, with nothing of this ride on the
                        // stack to come back to.
                        onDone = {
                            while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            backStack[0] = RiderRoute.ClassPicker
                        },
                        onFailed = { },
                    )
                }
                entry<RiderRoute.Promo> {
                    PromoScreen(
                        // **The server may say "go somewhere", and where is this application's to
                        // decide.** A deeplink is a name, not a route: the client maps the names it
                        // knows and ignores the rest, which is what keeps a backend from navigating
                        // somebody into a screen this build does not have.
                        onAction =
                            KompotActionHandler { action ->
                                if (action is NavigateAction && action.deeplink == RIDES_DEEPLINK) {
                                    while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                }
                            },
                    )
                }
                entry<RiderRoute.Callback> {
                    // **Nothing is drawn here and nothing should be.** The provider has just sent
                    // the browser back with a code in the query; what happens is an exchange and a
                    // navigation, and a screen would be a flash of something on the way past.
                    SignInCallback(
                        onDone = {
                            while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            backStack[0] = RiderRoute.ClassPicker
                        },
                    )
                }
            },
    )
}

/**
 * The other half of the redirect: the code out of the query, exchanged, and then away from here.
 *
 * **The query is read from the address bar rather than from a route parameter.** Navigation 3's keys
 * are `@Serializable` objects and the callback's payload is whatever the provider chose to put in the
 * URL — including a `state` this application must compare and an `error` it may send instead. Parsing
 * it where the browser keeps it is the honest place.
 */
@Composable
private fun SignInCallback(onDone: () -> Unit) {
    val session = koinInject<Session>()
    val bar = remember { addressBar() }
    LaunchedEffect(Unit) {
        val query = bar.queryAt()
        val code = query["code"]
        val state = query["state"]
        if (code != null && state != null) session.complete(code, state)
        onDone()
    }
}

/** The one deeplink this application answers. A name the server sends, not a path it chooses. */
private const val RIDES_DEEPLINK = "shashki://rides"

/**
 * The polymorphic registration Navigation 3 requires outside Android, where there is no reflection to
 * restore a back stack with. Every route is named here, and one that is not will fail to restore.
 */
private val SAVED_STATE =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(RiderRoute.ClassPicker::class)
                    subclass(RiderRoute.Callback::class)
                    subclass(RiderRoute.Finished::class)
                    subclass(RiderRoute.Matching::class)
                    subclass(RiderRoute.Trip::class)
                    subclass(RiderRoute.Promo::class)
                }
            }
    }
