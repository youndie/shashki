package io.github.youndie.shashki.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

public fun main() {
    embeddedServer(CIO, port = PORT, host = "0.0.0.0", module = Application::shashki).start(wait = true)
}

/** The port, here rather than in a config file until there is a config file worth having. */
private const val PORT: Int = 8080

/**
 * The whole application, which is one Ktor module and is meant to stay one.
 *
 * The rider, driver, dispatch, pricing and billing boundaries are packages under this one; see the
 * note in `build.gradle.kts` for why they are not Gradle modules yet.
 */
public fun Application.shashki() {
    install(ContentNegotiation) { json() }
    install(CallLogging)

    routing {
        // A liveness probe and, today, the only thing that proves the process starts. It is asserted
        // by `ApplicationTest`, so "the skeleton builds" is a claim a test makes rather than one the
        // build makes by compiling.
        get("/health") { call.respondText("ok") }
    }
}
