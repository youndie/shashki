package io.github.youndie.shashki.driver

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.auth.Session
import io.github.youndie.shashki.crash.installCrashReporting
import io.github.youndie.shashki.driver.feature.documents.ui.OnboardingScreen
import io.github.youndie.shashki.driver.feature.earnings.ui.EarningsScreen
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftScreen
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripScreen
import io.github.youndie.shashki.ui.DriverTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.nav.addressBar
import io.github.youndie.shashki.ui.nav.under
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

/**
 * The driver application: one Koin graph, one back stack, one theme.
 *
 * **The second bundle D10 chose, and the test of whether the first one was made of ports** (B-29).
 * `AddressBar` and `installCrashReporting` are bound here without a line of new implementation —
 * the address bar moved out of `:rider` into `:shared-ui` unchanged, which is what "port" was
 * supposed to mean.
 *
 * Amber rather than cyan, and there is no map surface in the graph: the driver's screens do not draw
 * one, and a binding nothing resolves is a dependency the reader has to disprove.
 */
@Composable
public fun DriverApp(
    config: DriverConfig,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    KoinApplication(koinConfiguration { modules(driverModule(config)) }) {
        CrashReporting(scope)

        val latin = kvadrantLatin()
        DriverTheme(latin = latin, typography = ShashkiTypography.of(latin)) {
            DriverNavigation(modifier)
        }
    }
}

/** B-10's hook, called — inside the Koin scope, so the reporter uses the application's own client. */
@Composable
private fun CrashReporting(scope: CoroutineScope) {
    val reporting = koinInject<CrashReporting>()
    DisposableEffect(reporting) {
        reporting.reporter?.let { installCrashReporting(it, scope) }
        onDispose { }
    }
}

/** The back stack, and the address bar kept in step with it — both directions, as in `:rider`. */
@Composable
private fun DriverNavigation(modifier: Modifier = Modifier) {
    // **Under the prefix this bundle is served at** (B-52): `DriverRoute.Shift` is `/` because a
    // route is a fact about the application, and `/driver` is where a deployment put it.
    val bar = remember { addressBar().under(DriverRoute.BASE) }
    val start = remember { DriverRoute.ofPath(bar.openedAt()) ?: DriverRoute.Shift }
    val backStack = rememberNavBackStack(SAVED_STATE, start)

    LaunchedEffect(backStack.lastOrNull()) {
        (backStack.lastOrNull() as? DriverRoute)?.let { bar.push(it.path) }
    }

    DisposableEffect(bar) {
        bar.onNavigate { path ->
            val route = DriverRoute.ofPath(path)
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
                entry<DriverRoute.Onboarding> {
                    OnboardingScreen(onFailed = { })
                }
                entry<DriverRoute.Earnings> {
                    EarningsScreen(onFailed = { })
                }
                entry<DriverRoute.Shift> {
                    ShiftScreen(
                        onAccepted = { rideId -> backStack.add(DriverRoute.Trip(rideId)) },
                        onEarnings = { backStack.add(DriverRoute.Earnings) },
                        // The line under the shift's title: the documents, which is the other thing
                        // a driver does between rides (B-47).
                        onDocuments = { backStack.add(DriverRoute.Onboarding) },
                        // An offer that went elsewhere leaves the driver where they were: still
                        // online, still waiting. There is nothing to navigate to.
                        onGone = { },
                        onFailed = { },
                    )
                }
                entry<DriverRoute.Callback> {
                    // Nothing is drawn: the provider has just sent the browser back with a code in
                    // the query, and what happens is an exchange and a navigation (B-52). The
                    // rider's callback is the same screen and the same three lines.
                    SignInCallback(
                        onDone = {
                            while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            backStack[0] = DriverRoute.Shift
                        },
                    )
                }
                entry<DriverRoute.Trip> { route ->
                    DriverTripScreen(
                        rideId = route.rideId,
                        onFinished = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                        onFailed = { },
                    )
                }
            },
    )
}

/**
 * The other half of the redirect: the code out of the query, exchanged, and then away from here.
 *
 * **The query is read from the address bar rather than from a route parameter** — the callback's
 * payload is whatever the provider put in the URL, including a `state` this application must compare
 * and an `error` it may send instead.
 */
@Composable
private fun SignInCallback(onDone: () -> Unit) {
    val session = koinInject<Session>()
    val bar = remember { addressBar().under(DriverRoute.BASE) }
    LaunchedEffect(Unit) {
        val query = bar.queryAt()
        val code = query["code"]
        val state = query["state"]
        if (code != null && state != null) session.complete(code, state)
        onDone()
    }
}

/** Navigation 3's polymorphic registration, which outside Android has no reflection to fall back on. */
private val SAVED_STATE =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(DriverRoute.Callback::class)
                    subclass(DriverRoute.Earnings::class)
                    subclass(DriverRoute.Onboarding::class)
                    subclass(DriverRoute.Shift::class)
                    subclass(DriverRoute.Trip::class)
                }
            }
    }
