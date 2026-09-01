package io.github.youndie.shashki.server

import io.github.youndie.shashki.server.db.DatabaseConfig
import io.github.youndie.shashki.server.db.DatabaseFactory
import io.github.youndie.shashki.server.dispatch.driverPositionRoutes
import io.github.youndie.shashki.server.feature.ride.domain.OfferNotFoundException
import io.github.youndie.shashki.server.feature.ride.domain.RideNotFoundException
import io.github.youndie.shashki.server.feature.ride.driverRoutes
import io.github.youndie.shashki.server.feature.ride.rideModule
import io.github.youndie.shashki.server.feature.ride.rideRoutes
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
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
import ru.workinprogress.petich.OptimisticLockException
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.SuspendedPetichSweeper
import ru.workinprogress.petich.outbox.OutboxRecord
import ru.workinprogress.petich.outbox.OutboxRelayWorker
import kotlin.time.Duration.Companion.seconds

public fun main() {
    val dataSource = DatabaseFactory.dataSource(DatabaseConfig.fromEnv())
    val applied = DatabaseFactory.migrate(dataSource)
    LoggerFactory.getLogger("shashki").info("applied {} migrations", applied)
    val database = DatabaseFactory.connect(dataSource)
    embeddedServer(CIO, port = PORT, host = "0.0.0.0") { shashki(database) }.start(wait = true)
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
        exception<IllegalArgumentException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorBody(e.message ?: "bad request"),
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
public fun Application.shashki(database: Database) {
    baseModule(listOf(rideModule(database, scope = this)))

    routing {
        rideRoutes()
        driverRoutes()
        driverPositionRoutes()
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
