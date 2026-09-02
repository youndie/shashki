package io.github.youndie.shashki.rider

import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.QuoteJourneyUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RideRepository
import io.github.youndie.shashki.rider.feature.ride.domain.WatchDriverUseCase
import io.github.youndie.shashki.ui.map.MapSurface
import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
        val koin = startKoin { modules(riderModule(CONFIG)) }.koin

        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<RideRepository>())
        assertNotNull(koin.get<QuoteJourneyUseCase>())
        assertNotNull(koin.get<RequestRideUseCase>())
        assertNotNull(koin.get<CancelRideUseCase>())
        assertNotNull(koin.get<ObserveRideUseCase>())
        assertNotNull(koin.get<WatchDriverUseCase>())
        assertNotNull(koin.get<MapSurface>())
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
        val koin = startKoin { modules(riderModule(CONFIG)) }.koin

        assertNull(koin.get<CrashReporting>().reporter)
    }

    @Test
    fun `a katcher configured resolves to a reporter`() {
        val koin =
            startKoin {
                modules(riderModule(CONFIG.copy(katcherUrl = "https://katcher.example", katcherAppKey = "key")))
            }.koin

        assertNotNull(koin.get<CrashReporting>().reporter)
    }

    private companion object {
        val CONFIG =
            RiderConfig(
                serverUrl = "http://127.0.0.1:8080",
                riderId = "rider-1",
                paymentMethodId = "card-4417",
                katcherUrl = null,
                katcherAppKey = null,
                release = "test",
            )
    }
}
