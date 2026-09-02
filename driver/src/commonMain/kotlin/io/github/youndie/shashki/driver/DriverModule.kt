package io.github.youndie.shashki.driver

import io.github.youndie.shashki.crash.CrashReporter
import io.github.youndie.shashki.crash.CrashReporterConfig
import io.github.youndie.shashki.driver.feature.offer.data.HttpOfferRepository
import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.OfferRepository
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.shift.data.WebSocketShiftRepository
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.ShiftRepository
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftViewModel
import io.github.youndie.shashki.driver.feature.trip.data.HttpTripRepository
import io.github.youndie.shashki.driver.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripViewModel
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The crash reporter, or the fact that none is configured. The rider's wrapper, for the same reason. */
public class CrashReporting(
    public val reporter: CrashReporter?,
)

/**
 * Where the server is and who the driver is until a token says so.
 *
 * **`rideClass` and `rating` are here because the socket believes them**, which `DriverReport`'s own
 * note calls a seam: a real system reads both from the driver's row. They are configuration in this
 * bundle so that the hole is visible in one place rather than spread through the screens.
 */
public data class DriverConfig(
    val serverUrl: String,
    val driverId: String,
    val rideClass: RideClass,
    val rating: Double,
    val at: GeoPoint,
    val katcherUrl: String?,
    val katcherAppKey: String?,
    val release: String,
) {
    public companion object {
        public val LJUBLJANA_CENTRE: GeoPoint = GeoPoint(46.0511, 14.5051)
    }
}

/**
 * The driver's graph.
 *
 * **Every use case is an explicit lambda and none is `factoryOf`** — `GoOnlineUseCase(shift,
 * interval = 4.seconds)` would have Koin looking for a binding of `Duration` and failing at run
 * time with the compiler silent. The rider's module carries the same note and the server hit the
 * trap first.
 */
public fun driverModule(config: DriverConfig): Module =
    module {
        single { config }
        single {
            HttpClient {
                install(Resources)
                // The one plugin the rider's client does not install. A shift *is* this connection.
                install(WebSockets)
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    url(config.serverUrl)
                    contentType(ContentType.Application.Json)
                }
                // **404 and 409 are answers this application reads**, so they must not be thrown
                // before it sees them: no offer on the board, and an offer that moved on.
                expectSuccess = false
            }
        }

        single<ShiftRepository> { WebSocketShiftRepository(get(), config.serverUrl) }
        factory { GoOnlineUseCase(get()) }

        single<OfferRepository> { HttpOfferRepository(get()) }
        factory { WatchOfferUseCase(get()) }
        factory { AnswerOfferUseCase(get()) }

        single<TripRepository> { HttpTripRepository(get()) }
        factory { ObserveTripUseCase(get()) }
        factory { AdvanceTripUseCase(get()) }

        single {
            CrashReporting(
                config.katcherUrl?.let { url ->
                    config.katcherAppKey?.let { key ->
                        CrashReporter(
                            get(),
                            CrashReporterConfig(serverUrl = url, appKey = key, release = config.release),
                        )
                    }
                },
            )
        }

        viewModel {
            ShiftViewModel(
                driverId = config.driverId,
                rideClass = config.rideClass,
                rating = config.rating,
                at = config.at,
                goOnline = get(),
                watchOffer = get(),
                answerOffer = get(),
            )
        }
        viewModel { (rideId: String) -> DriverTripViewModel(rideId, config.driverId, get(), get()) }
    }
