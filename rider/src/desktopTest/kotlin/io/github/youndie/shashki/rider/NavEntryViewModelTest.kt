package io.github.youndie.shashki.rider

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.youndie.shashki.rider.feature.ride.ui.MatchingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * **Two rides, two matching screens, two view models** (B-69).
 *
 * This is the property the product lost by leaving `NavDisplay` to its defaults. A `koinViewModel()`
 * resolves against the nearest `ViewModelStoreOwner`; with no per-entry store that is the window's,
 * so `Matching("ride-2")` was handed the view model built for `Matching("ride-1")` — already at
 * "no cars", polling a ride that had ended — and the second order looked like an empty city before
 * the first driver had been asked. The driver's second trip failed the same way with the first
 * trip's `COMPLETED`.
 *
 * **Composed rather than resolved**, because that is where the defect lives: `RiderGraphTest`
 * builds every view model from the graph and passed throughout, and so did every golden — neither
 * navigates. This one pushes two entries of one route through the application's own decorators and
 * asks each what ride it was built for.
 */
@OptIn(ExperimentalTestApi::class)
class NavEntryViewModelTest {
    @Test
    fun `a second entry of the same route gets a view model of its own`() {
        val seen = mutableMapOf<String, MatchingViewModel>()
        val backStack = mutableStateListOf<RiderRoute>(RiderRoute.Matching("ride-1"))

        runComposeUiTest {
            setContent {
                KoinApplication(koinConfiguration { modules(riderModule(CONFIG, CoroutineScope(SupervisorJob()))) }) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        entryProvider =
                            entryProvider {
                                entry<RiderRoute.Matching> { route ->
                                    val viewModel: MatchingViewModel =
                                        koinViewModel(parameters = { parametersOf(route.rideId) })
                                    seen[route.rideId] = viewModel
                                }
                            },
                    )
                }
            }
            waitForIdle()
            backStack.add(RiderRoute.Matching("ride-2"))
            waitForIdle()
        }

        assertEquals(setOf("ride-1", "ride-2"), seen.keys)
        assertNotSame(seen["ride-1"], seen["ride-2"], "the second ride was handed the first ride's view model")
    }

    private companion object {
        val CONFIG =
            RiderConfig(
                serverUrl = "http://127.0.0.1:1",
                riderId = "rider-1",
                paymentMethodId = "card-1",
                tilesUrl = null,
                signIn = null,
                katcherUrl = null,
                katcherAppKey = null,
                release = "test",
            )
    }
}
