package io.github.youndie.shashki.rider

import io.github.youndie.shashki.crash.CrashReporter
import io.github.youndie.shashki.crash.CrashReporterConfig
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.rider.feature.promo.data.HttpPromoRepository
import io.github.youndie.shashki.rider.feature.promo.domain.LoadPromoUseCase
import io.github.youndie.shashki.rider.feature.promo.domain.PromoRepository
import io.github.youndie.shashki.rider.feature.promo.ui.PromoViewModel
import io.github.youndie.shashki.rider.feature.ride.data.HttpRideRepository
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.QuoteJourneyUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RideRepository
import io.github.youndie.shashki.rider.feature.ride.domain.WatchDriverUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerViewModel
import io.github.youndie.shashki.rider.feature.ride.ui.TripViewModel
import io.github.youndie.shashki.ui.map.CanvasMapSurface
import io.github.youndie.shashki.ui.map.MapSurface
import io.github.youndie.shashki.ui.map.tiles.HttpRangeReader
import io.github.youndie.shashki.ui.map.tiles.NoTiles
import io.github.youndie.shashki.ui.map.tiles.PmtilesArchive
import io.github.youndie.shashki.ui.map.tiles.PmtilesTileSource
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The crash reporter, or the fact that none is configured. */
public class CrashReporting(
    public val reporter: CrashReporter?,
)

/** Where the server is and who the rider is until B-26 signs them in. */
public data class RiderConfig(
    val serverUrl: String,
    val riderId: String,
    val paymentMethodId: String,
    /** Where `city.pmtiles` is served from, or `null` for a map with no streets on it. */
    val tilesUrl: String?,
    val katcherUrl: String?,
    val katcherAppKey: String?,
    val release: String,
    val pickup: GeoPoint = LJUBLJANA_CENTRE,
    val dropoff: GeoPoint = LJUBLJANA_AIRPORT,
) {
    public companion object {
        public val LJUBLJANA_CENTRE: GeoPoint = GeoPoint(46.0511, 14.5051)
        public val LJUBLJANA_AIRPORT: GeoPoint = GeoPoint(46.2237, 14.4576)
    }
}

/**
 * The rider's graph.
 *
 * **Every use case is an explicit lambda and none is `factoryOf`.** `factoryOf` resolves *every*
 * constructor parameter through Koin, default values included — `ObserveRideUseCase(rides, interval
 * = 2.seconds)` would have Koin looking for a binding of `Duration`, and it fails at run time on the
 * first request with the compiler silent. The server hit that exact trap in B-11 and it is written
 * into the skill; the cost of avoiding it here is one lambda each.
 */
public fun riderModule(config: RiderConfig): Module =
    module {
        single { config }
        single {
            HttpClient {
                install(Resources)
                install(ContentNegotiation) {
                    // `ignoreUnknownKeys`, because the server is deployed separately: a field added
                    // there must not take this bundle down until it is rebuilt.
                    json(Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    url(config.serverUrl)
                    contentType(ContentType.Application.Json)
                }
            }
        }
        single<RideRepository> { HttpRideRepository(get()) }

        single<PromoRepository> { HttpPromoRepository(get()) }
        factory { LoadPromoUseCase(get()) }

        factory { QuoteJourneyUseCase(get()) }
        factory { RequestRideUseCase(get()) }
        factory { CancelRideUseCase(get()) }
        factory { ObserveRideUseCase(get()) }
        factory { WatchDriverUseCase(get()) }

        // **The basemap, over ranged HTTP from wherever the archive is hosted** (B-30). Not from
        // this server: the tiles are a static object and shashki is not a tile server, which is why
        // the address is configuration rather than a route in `:protocol`.
        //
        // No archive configured is a running configuration, not a broken one — the surface paints
        // the style's own background and draws the road, the car and the pins on it, in the right
        // place. That was the whole product's map until this item and it is still the demo's when
        // nobody has uploaded a city.
        single<MapSurface> {
            val tiles =
                config.tilesUrl?.let { url ->
                    PmtilesTileSource { PmtilesArchive.open(HttpRangeReader(get(), url)) }
                } ?: NoTiles
            CanvasMapSurface(tiles, TilePalette.Dark)
        }

        // **A wrapper rather than a nullable binding.** Koin resolves by type and a `single` that
        // returned null would fail at injection with a message about the type rather than about the
        // configuration. The absence is a value here, and it is a value the reader can see.
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
            ClassPickerViewModel(
                quoteJourney = get(),
                requestRide = get(),
                pickup = config.pickup,
                dropoff = config.dropoff,
                riderId = config.riderId,
                paymentMethodId = config.paymentMethodId,
            )
        }
        viewModel { (rideId: String) -> TripViewModel(rideId, get(), get(), get()) }
        viewModel { PromoViewModel(get()) }
    }
