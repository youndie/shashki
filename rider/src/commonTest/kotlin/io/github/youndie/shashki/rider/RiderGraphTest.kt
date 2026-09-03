package io.github.youndie.shashki.rider

import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.shashki.rider.feature.promo.ui.PromoViewModel
import io.github.youndie.shashki.rider.feature.receipt.ui.ReceiptViewModel
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.QuoteJourneyUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RideRepository
import io.github.youndie.shashki.rider.feature.ride.domain.WatchDriverUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerViewModel
import io.github.youndie.shashki.rider.feature.ride.ui.TripViewModel
import io.github.youndie.shashki.ui.map.MapSurface
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Every definition is resolved, not merely declared.**
 *
 * The trap this exists for is the project's own, twice over: `singleOf` and `factoryOf` resolve
 * *every* constructor parameter through Koin, defaults included, so `ObserveRideUseCase(rides,
 * interval = 2.seconds)` would send Koin looking for a `Duration` — and it fails at run time on the
 * first request with the compiler silent. The server met it in B-11; this module writes every use
 * case as an explicit lambda for that reason, and this is what proves the lambdas are right.
 */
class RiderGraphTest {
    @AfterTest
    fun stop(): Unit = stopKoin()

    @Test
    fun `every definition the rider needs resolves`() {
        val koin = startKoin { modules(riderModule(CONFIG, noScope)) }.koin

        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<RideRepository>())
        assertNotNull(koin.get<QuoteJourneyUseCase>())
        assertNotNull(koin.get<RequestRideUseCase>())
        assertNotNull(koin.get<CancelRideUseCase>())
        assertNotNull(koin.get<ObserveRideUseCase>())
        assertNotNull(koin.get<WatchDriverUseCase>())
        assertNotNull(koin.get<MapSurface>())
        // **Both sinks, because the second is qualified and a qualifier is a string.** The receipt
        // screen injects `named("receipt")`; a binding declared under any other name resolves to
        // nothing at the moment the screen is drawn, which is a crash on a screen nobody's test
        // opens. Two `get`s here are what make the name a contract.
        assertNotNull(koin.get<KompotDegradationSink>(), "the promo screen's")
        assertNotNull(koin.get<KompotDegradationSink>(named(RECEIPT_SCREEN)), "the receipt's (B-61)")
    }

    /**
     * **The view models, built rather than declared — the half this test did not have.**
     *
     * Every test above resolved a repository or a use case, which is what the module is *made of*;
     * what the application resolves first is a view model, and none was ever built here. The desktop
     * rider, run against the stand for the first time, died on `Could not create instance for
     * ClassPickerViewModel` — `init { load() }` reaches `viewModelScope`, which is
     * `Dispatchers.Main.immediate`, and the JVM window had no Main dispatcher at all because
     * `kotlinx-coroutines-swing` was not on the desktop runtime classpath. Nothing else in this
     * repository builds a view model: the goldens photograph the `Content` composables, which is the
     * point of the split and also its blind spot.
     *
     * So this constructs each one, which is the only thing that runs an `init`. `TripViewModel`
     * takes the ride's id as a parameter, exactly as the screen passes it.
     */
    @Test
    fun `every view model can actually be constructed`() {
        val koin = startKoin { modules(riderModule(CONFIG, noScope)) }.koin

        assertNotNull(koin.get<ClassPickerViewModel>(), "the first screen of the application")
        assertNotNull(koin.get<PromoViewModel>())
        assertNotNull(
            koin.get<TripViewModel> { parametersOf("ride-1") },
            "the trip screen is resolved with the ride's id, as the route passes it",
        )
        assertNotNull(
            koin.get<ReceiptViewModel> { parametersOf("ride-1") },
            "R9·b is resolved the same way, and its sink is a qualified binding (B-61)",
        )
    }

    /**
     * The absence of a katcher is a value, and it resolves.
     *
     * A `single` that returned `null` would fail at injection with a message about the type rather
     * than about the configuration — which is why the reporter is behind [CrashReporting] and why
     * this asserts both halves of it.
     */
    @Test
    fun `no katcher configured resolves to a reporting with no reporter`() {
        val koin = startKoin { modules(riderModule(CONFIG, noScope)) }.koin

        assertNull(koin.get<CrashReporting>().reporter)
    }

    @Test
    fun `a katcher configured resolves to a reporter`() {
        val koin =
            startKoin {
                modules(
                    riderModule(CONFIG.copy(katcherUrl = "https://katcher.example", katcherAppKey = "key"), noScope),
                )
            }.koin

        assertNotNull(koin.get<CrashReporting>().reporter)
    }

    /**
     * **A map with no archive still resolves**, which is the half of B-30 that is easy to break by
     * making the basemap a requirement. A demo pointed at no tiles draws the style's own background
     * with the road and the car on it; a graph that refused to build without an archive would take
     * every screen down with the map.
     */
    @Test
    fun `the map resolves with and without an archive to fetch from`() {
        val koin = startKoin { modules(riderModule(CONFIG, noScope)) }.koin
        assertNotNull(koin.get<MapSurface>())
        stopKoin()

        val withTiles =
            startKoin {
                modules(riderModule(CONFIG.copy(tilesUrl = "https://tiles.example/city.pmtiles"), noScope))
            }.koin
        assertNotNull(withTiles.get<MapSurface>())
    }

    /** Nothing is launched on it: the graph is built and resolved, never used. */
    private val noScope = CoroutineScope(SupervisorJob())

    private companion object {
        val CONFIG =
            RiderConfig(
                serverUrl = "http://127.0.0.1:8080",
                riderId = "rider-1",
                paymentMethodId = "card-4417",
                tilesUrl = null,
                signIn = null,
                katcherUrl = null,
                katcherAppKey = null,
                release = "test",
            )
    }
}
