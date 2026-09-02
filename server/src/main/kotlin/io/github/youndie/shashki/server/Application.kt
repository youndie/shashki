package io.github.youndie.shashki.server

import io.github.youndie.shashki.server.db.DatabaseConfig
import io.github.youndie.shashki.server.db.DatabaseFactory
import io.github.youndie.shashki.server.dispatch.driverPositionRoutes
import io.github.youndie.shashki.server.feature.auth.AuthConfig
import io.github.youndie.shashki.server.feature.promo.promoRoutes
import io.github.youndie.shashki.server.feature.quote.quoteRoutes
import io.github.youndie.shashki.server.feature.ride.domain.OfferGoneException
import io.github.youndie.shashki.server.feature.ride.domain.OfferNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.driverRoutes
import io.github.youndie.shashki.server.feature.ride.rideModule
import io.github.youndie.shashki.server.feature.ride.rideRoutes
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.route.RoutingConfig
import io.github.youndie.shashki.server.feature.route.data.NoRouteException
import io.github.youndie.shashki.server.feature.route.routeRoutes
import io.github.youndie.shashki.server.feature.settlement.domain.NothingToSettleException
import io.github.youndie.shashki.server.feature.trip.domain.NotThisDriversRideException
import io.github.youndie.shashki.server.feature.trip.domain.OutOfOrderTransitionException
import io.github.youndie.shashki.server.feature.trip.tripRoutes
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import ru.workinprogress.oidc.OidcConfig
import ru.workinprogress.oidc.configureAuth
import ru.workinprogress.petich.OptimisticLockException
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.SuspendedPetichSweeper
import ru.workinprogress.petich.outbox.OutboxRecord
import ru.workinprogress.petich.outbox.OutboxRelayWorker
import java.io.File
import kotlin.time.Duration.Companion.seconds

public fun main() {
    val dataSource = DatabaseFactory.dataSource(DatabaseConfig.fromEnv())
    val applied = DatabaseFactory.migrate(dataSource)
    LoggerFactory.getLogger("shashki").info("applied {} migrations", applied)
    val database = DatabaseFactory.connect(dataSource)
    val oidc = AuthConfig.fromEnv()
    val bundles = BundleConfig.root()
    val page = pageValues()
    embeddedServer(CIO, port = PORT, host = "0.0.0.0") {
        shashki(database, oidc = oidc, bundles = bundles, page = page)
    }.start(wait = true)
}

/** The port, here rather than in a config file until there is a config file worth having. */
private const val PORT: Int = 8080

private val WEBSOCKET_PING = 15.seconds

/**
 * Everything that needs no database: the plugins, the error mapping and the health probe. Split
 * out so a test — and the probe itself — can have a server without a database, and so the list of
 * plugins is readable on its own.
 */
public fun Application.baseModule(modules: List<Module> = emptyList()) {
    install(Koin) {
        slf4jLogger()
        modules(modules)
    }
    install(ContentNegotiation) { json() }
    install(Resources)
    install(CallLogging)
    // A ping, because a driver's socket sits idle between reports and the first thing a mobile
    // network does with an idle socket is forget about it.
    install(WebSockets) { pingPeriod = WEBSOCKET_PING }

    // Before routing, and the reason is order of thought rather than of execution: a route written
    // after this exists answers `.getOrThrow()` and stops, because the mapping is already here.
    install(StatusPages) {
        exception<RideNotFoundException> { call, e ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorBody(e.message ?: "not found"),
            )
        }
        exception<OfferNotFoundException> { call, e ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorBody(e.message ?: "not found"),
            )
        }
        // 409 rather than 404: the offer existed and the driver's answer was well formed — somebody
        // else has it now. That is a race the client should say out loud, not a missing resource.
        // A transition out of order, or a ride that is not this driver's: the request is well formed
        // and the server understood it, and the answer is about state rather than about syntax.
        exception<OutOfOrderTransitionException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ErrorBody(e.message ?: "not the next state"))
        }
        exception<NotThisDriversRideException> { call, e ->
            // 404 rather than 403: confirming that somebody else's ride exists is itself an answer.
            call.respond(HttpStatusCode.NotFound, ErrorBody(e.message ?: "not found"))
        }
        exception<NothingToSettleException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ErrorBody(e.message ?: "nothing to settle"))
        }
        exception<OfferGoneException> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorBody(e.message ?: "the offer has gone"),
            )
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorBody(e.message ?: "bad request"),
            )
        }
        // 422 rather than 400: the request is well formed and the server understood it — there is
        // simply no road. A 400 would tell the client to fix its message, which is the wrong advice.
        exception<NoRouteException> { call, e ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorBody(e.message ?: "no route"),
            )
        }
        exception<OptimisticLockException> { call, _ ->
            call.respond(HttpStatusCode.Conflict, ErrorBody("concurrent modification, retry"))
        }
    }

    routing {
        // A liveness probe and, on a server with no database, the only thing that proves the
        // process starts. Asserted by `ApplicationTest`.
        get("/health") { call.respondText("ok") }
    }
}

/**
 * The whole application, which is one Ktor module and is meant to stay one.
 *
 * The rider, driver, dispatch, pricing and billing boundaries are packages under this one; see the
 * note in `build.gradle.kts` for why they are not Gradle modules yet.
 */
public fun Application.shashki(
    database: Database,
    routeEstimator: RouteEstimator = RoutingConfig.estimator(),
    oidc: OidcConfig? = null,
    /**
     * Where the browser bundles are, or `null` for a server that only answers the API.
     *
     * `null` by default and read from the environment in `main`, for the reason the provider is:
     * a parameter that reads `System.getenv` by default makes every test's behaviour depend on the
     * shell it was started from.
     */
    bundles: File? = null,
    /** What the served page tells the bundles about this deployment. */
    page: Map<String, String> = emptyMap(),
) {
    baseModule(listOf(rideModule(database, scope = this, routeEstimator = routeEstimator)))

    // **Verification is installed only when a provider is named, and that is a switch with a test
    // on both sides.** The environment is read in `main` rather than defaulted here: a parameter
    // that reads `System.getenv` by default makes every test's behaviour depend on the shell it was
    // started from, and the one that would change is whether the rider's routes need a token. A demo pointed at no provider must still run — there is nobody to sign in
    // against — and a guard that is off by default is a guard nobody notices is off, so
    // `ProtectedRidesTest` runs the same routes with it on and requires a 401 without a token.
    if (oidc != null) {
        // Any token this provider issued to the rider client. There are no roles yet: a rider is
        // whoever signed in, and what they may do to a *particular* ride is ownership rather than a
        // role — which `RideRepository` will answer when the token carries the rider's id (B-09).
        configureAuth(oidc) { data -> data.azp == oidc.clientId }
    }

    routing {
        rideRoutes(protected = oidc != null)
        tripRoutes()
        routeRoutes()
        quoteRoutes()
        promoRoutes()
        driverRoutes()
        driverPositionRoutes()

        // **Last, and a test rather than a hope.** `default("index.html")` under `/` answers any
        // path it is given, so the question is whether a literal `/api/...` route still wins. Ktor
        // matches by specificity rather than by declaration order and it does — `BundleRoutingTest`
        // is what says so, because the failure mode is the whole API returning a web page.
        configScript(page)
        bundleRoutes(bundles)
    }

    // Two workers the saga cannot do without and the request path never sees. The sweeper rolls
    // back sagas that suspended for a driver nobody came back for (B-12's deadline); the relay
    // delivers what the outbox holds. Both stop with the application, through its own scope.
    val storage = get<SagaStorage>()
    val engine = get<PetichEngine>()
    SuspendedPetichSweeper(repository = storage.petiches, engineFor = { engine }, clock = get<PetichClock>())
        .start(this)
    // Logging is the publisher until the broker is wired; an event that reaches the log has left
    // the outbox, and that is the property B-11 is about. booblik is a later item.
    OutboxRelayWorker(repository = storage.outbox, publisher = LoggingPublisher).start(this)
}

private object LoggingPublisher : ru.workinprogress.petich.outbox.OutboxPublisher {
    private val log = LoggerFactory.getLogger("shashki.outbox")

    override suspend fun publish(event: OutboxRecord) {
        log.info("outbox → {} {} {}", event.type, event.id, event.payload)
    }
}

@Serializable
private data class ErrorBody(
    val error: String,
)
