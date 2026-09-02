package io.github.youndie.shashki.driver

import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.OfferRepository
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.ShiftRepository
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.protocol.RideClass
import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Every definition is resolved, not merely declared** — the rider's test, for the same trap.
 *
 * `GoOnlineUseCase(shift, interval = 4.seconds)` and `WatchOfferUseCase(offers, interval =
 * 2.seconds)` both have a `Duration` with a default, and `factoryOf` would send Koin looking for a
 * binding of it: a failure at run time with the compiler silent.
 */
class DriverGraphTest {
    @AfterTest
    fun stop(): Unit = stopKoin()

    @Test
    fun `every definition the driver needs resolves`() {
        val koin = startKoin { modules(driverModule(CONFIG)) }.koin

        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<ShiftRepository>())
        assertNotNull(koin.get<GoOnlineUseCase>())
        assertNotNull(koin.get<OfferRepository>())
        assertNotNull(koin.get<WatchOfferUseCase>())
        assertNotNull(koin.get<AnswerOfferUseCase>())
        assertNotNull(koin.get<TripRepository>())
        assertNotNull(koin.get<ObserveTripUseCase>())
    }

    @Test
    fun `no katcher configured resolves to a reporting with no reporter`() {
        val koin = startKoin { modules(driverModule(CONFIG)) }.koin

        assertNull(koin.get<CrashReporting>().reporter)
    }

    @Test
    fun `a katcher configured resolves to a reporter`() {
        val koin =
            startKoin {
                modules(driverModule(CONFIG.copy(katcherUrl = "https://katcher.example", katcherAppKey = "key")))
            }.koin

        assertNotNull(koin.get<CrashReporting>().reporter)
    }

    private companion object {
        val CONFIG =
            DriverConfig(
                serverUrl = "http://127.0.0.1:8080",
                driverId = "driver-1",
                rideClass = RideClass.ECONOMY,
                rating = 4.9,
                at = DriverConfig.LJUBLJANA_CENTRE,
                katcherUrl = null,
                katcherAppKey = null,
                release = "test",
            )
    }
}
