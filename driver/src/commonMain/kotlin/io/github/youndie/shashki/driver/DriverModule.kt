package io.github.youndie.shashki.driver

import io.github.youndie.shashki.auth.HttpTokenExchange
import io.github.youndie.shashki.auth.Session
import io.github.youndie.shashki.auth.SignInConfig
import io.github.youndie.shashki.auth.TokenExchange
import io.github.youndie.shashki.auth.TokenStore
import io.github.youndie.shashki.auth.redirectTo
import io.github.youndie.shashki.auth.tokenStore
import io.github.youndie.shashki.crash.CrashReporter
import io.github.youndie.shashki.crash.CrashReporterConfig
import io.github.youndie.shashki.driver.feature.documents.data.HttpDocumentsRepository
import io.github.youndie.shashki.driver.feature.documents.domain.DocumentsRepository
import io.github.youndie.shashki.driver.feature.documents.domain.ReadDocumentsUseCase
import io.github.youndie.shashki.driver.feature.documents.domain.UploadDocumentUseCase
import io.github.youndie.shashki.driver.feature.documents.ui.OnboardingViewModel
import io.github.youndie.shashki.driver.feature.earnings.data.HttpEarningsRepository
import io.github.youndie.shashki.driver.feature.earnings.domain.EarningsRepository
import io.github.youndie.shashki.driver.feature.earnings.domain.ReadEarningsUseCase
import io.github.youndie.shashki.driver.feature.earnings.ui.EarningsViewModel
import io.github.youndie.shashki.driver.feature.offer.data.HttpOfferRepository
import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.OfferRepository
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.shift.data.DevicePositionFixes
import io.github.youndie.shashki.driver.feature.shift.data.WebSocketShiftRepository
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.driver.feature.shift.domain.PositionFixes
import io.github.youndie.shashki.driver.feature.shift.domain.ShiftRepository
import io.github.youndie.shashki.driver.feature.shift.ui.ShiftViewModel
import io.github.youndie.shashki.driver.feature.trip.data.HttpTripRepository
import io.github.youndie.shashki.driver.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ReadTripSummaryUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.driver.feature.trip.ui.DriverTripViewModel
import io.github.youndie.shashki.driver.feature.trip.ui.TripSummaryViewModel
import io.github.youndie.shashki.protocol.DriverTicket
import io.github.youndie.shashki.protocol.DriverTickets
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.ui.map.CanvasMapSurface
import io.github.youndie.shashki.ui.map.MapSurface
import io.github.youndie.shashki.ui.map.tiles.HttpRangeReader
import io.github.youndie.shashki.ui.map.tiles.NoTiles
import io.github.youndie.shashki.ui.map.tiles.PmtilesArchive
import io.github.youndie.shashki.ui.map.tiles.PmtilesTileSource
import io.github.youndie.shashki.ui.map.tiles.TilePalette
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

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
    /**
     * Who this driver is when nobody has signed in.
     *
     * **Once a provider is configured the server ignores it** (B-52): the identity of every driver
     * request is the token's subject, and this is what is left for the demo that signs nobody in.
     */
    val driverId: String,
    val rideClass: RideClass,
    val rating: Double,
    val at: GeoPoint,
    val katcherUrl: String?,
    val katcherAppKey: String?,
    val release: String,
    /** Where `city.pmtiles` is, or `null` for a map with no basemap — the rider's own rule (B-75). */
    val tilesUrl: String? = null,
    /**
     * The provider, or `null` for a demo that signs nobody in — the rider's `signIn` exactly (B-52).
     *
     * **Both halves have to agree**: a server with `SHASHKI_OIDC_ISSUER` refuses every driver route
     * without a token, and a bundle with no `signIn` sends none. The failure is loud on one side and
     * silent on the other, which is why the note is in both configs.
     */
    val signIn: SignInConfig? = null,
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
@Suppress(
    "ktlint:kapkan:wall-clock",
    "the composition root is where the clock is bound; the shift screen counts a duration it was handed (B-29)",
)
public fun driverModule(config: DriverConfig): Module =
    module {
        single { config }
        // **The session first**, because the client below asks it for a token on every request —
        // the rider's module says the same thing in the same order (B-52).
        single<TokenStore> { tokenStore() }
        single<TokenExchange> { HttpTokenExchange(get(named(PROVIDER_CLIENT))) }
        single { Session(store = get(), config = config.signIn, exchange = get(), redirect = ::redirectTo) }

        // The provider's client: no base URL and no bearer token. One request per sign-in, to a
        // different service, and it must not carry the token it is about to replace.
        single(named(PROVIDER_CLIENT)) { HttpClient() }

        single {
            val session = get<Session>()
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
                    // One place, so a driver route added tomorrow is authenticated tomorrow.
                    session.token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                // **404 and 409 are answers this application reads**, so they must not be thrown
                // before it sees them: no offer on the board, and an offer that moved on.
                expectSuccess = false
                HttpResponseValidator {
                    validateResponse { response ->
                        // A 401 is a sign-in, not an error banner — and on this bundle it is also
                        // what a driver sees when a shift outlives a token.
                        if (response.status == HttpStatusCode.Unauthorized && session.configured) {
                            session.renew()
                        }
                    }
                }
            }
        }

        single<ShiftRepository> {
            val client = get<HttpClient>()
            val session = get<Session>()
            WebSocketShiftRepository(
                client = client,
                serverUrl = config.serverUrl,
                ticket = {
                    // Nobody signed in is the demo configuration, and the server's socket is open
                    // in it — the two halves agree or the shift does not start.
                    if (session.configured) client.post(DriverTickets()).body<DriverTicket>().value else null
                },
            )
        }
        // The platform's own positions are behind this binding, and on the desktop there are none
        // (B-49). The `device` parameter has a default, so the lambda is explicit — see the note on
        // this module.
        single<PositionFixes> { DevicePositionFixes() }
        factory { GoOnlineUseCase(get(), get()) }

        single<OfferRepository> { HttpOfferRepository(get()) }
        factory { WatchOfferUseCase(get()) }
        factory { AnswerOfferUseCase(get()) }

        // **Who this bundle is, asked rather than captured** (B-53). Everything below used to take
        // `config.driverId`, which is `SHASHKI_DRIVER_ID` — a value the token contradicts the moment
        // anybody signs in, and the socket answers a contradiction by dropping the frame.
        single<DriverIdentity> { TokenDriverIdentity(get(), config.driverId) }

        single<DocumentsRepository> { HttpDocumentsRepository(get(), get()) }
        factory { ReadDocumentsUseCase(get()) }
        factory { UploadDocumentUseCase(get()) }

        single<EarningsRepository> { HttpEarningsRepository(get(), get()) }
        factory { ReadEarningsUseCase(get()) }

        single<TripRepository> { HttpTripRepository(get()) }
        // **The same map the rider has, for the same reason** (B-75): D4 draws the road to the pickup
        // and then to the drop-off, and a driver is the person who needs it most. No archive is a
        // running configuration — the style's own background with the road and the car on it.
        single<MapSurface> {
            val tiles =
                config.tilesUrl?.let { url ->
                    PmtilesTileSource { PmtilesArchive.open(HttpRangeReader(get(), url)) }
                } ?: NoTiles
            CanvasMapSurface(tiles, TilePalette.Dark)
        }
        factory { ObserveTripUseCase(get()) }
        factory { AdvanceTripUseCase(get()) }
        factory { ReadTripSummaryUseCase(get(), get()) }

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
                identity = get(),
                rideClass = config.rideClass,
                rating = config.rating,
                at = config.at,
                goOnline = get(),
                watchOffer = get(),
                answerOffer = get(),
                readEarnings = get(),
                now = { Clock.System.now().toEpochMilliseconds() },
            )
        }
        viewModel { (rideId: String) ->
            DriverTripViewModel(rideId, get(), get(), get(), positions = get(), configured = config.at, roads = get())
        }
        viewModel { (rideId: String) -> TripSummaryViewModel(rideId, get()) }
        viewModel { EarningsViewModel(get()) }
        // The picker is the platform's and is left at its default here: the graph has nothing to say
        // about a file dialog, and a test hands in its own.
        viewModel { OnboardingViewModel(readDocuments = get(), uploadDocument = get()) }
    }

/** The provider's client, told apart from the application's by a name rather than by a type. */
private const val PROVIDER_CLIENT = "provider"
