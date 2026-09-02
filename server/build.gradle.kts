plugins {
    alias(wip.plugins.kotlinJvm)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.lint")
    application
}

application {
    mainClass.set("io.github.youndie.shashki.server.ApplicationKt")
}

// One process, and the brief says why: the "microservice" story here is told by the saga and the
// broker, not by ten deployments. The boundaries the research committed to — rider-api, driver-api,
// dispatch, pricing, billing — are packages until there is enough code for a Gradle module to mean
// something; splitting an empty server into five of them would be inventing boundaries before the
// domain has any.
dependencies {
    implementation(projects.protocol)

    implementation(platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.resources)
    // Driver positions arrive on a socket and go straight into the geo-index — never into the
    // broker (research §1.6a). The client half is here because the simulator is a client of that
    // same socket rather than a back door into the index.
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.client.resources)
    implementation(libs.ktor.client.cio)
    implementation(wip.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

    // The order saga: the engine, its Postgres repository, and the outbox relay. petich-postgres
    // ships no driver, no pool and no DDL on purpose — those three are the application's, below.
    // The one screen the server owns, as a tree. `kompot-core` is the component model and
    // `kompot-standard` its vocabulary — neither carries Compose, which is what lets a headless
    // server build a screen at all. See B-32 and research §2 D11.
    implementation(libs.kompot.core)
    implementation(libs.kompot.standard)

    // The receipt, over SMTP written from the RFC rather than through a JVM mail library — which is
    // the only thing this part of the demo demonstrates. See B-14 and research §1.6d.
    implementation(libs.smtpkn.client)
    implementation(libs.smtpkn.transportKtor)
    implementation(libs.smtpkn.tlsJvm)
    implementation(libs.smtpkn.mime)
    implementation(libs.smtpkn.sasl)

    // Routing on the city's own extract, in this process. See B-23 and `GraphHopperRouteEstimator`.
    implementation(libs.graphhopper.core)

    implementation(libs.petich.core)
    implementation(libs.petich.postgres)
    implementation(libs.petich.outboxCore)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(platform(wip.koin.bom))
    implementation(wip.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    testImplementation(kotlin("test"))
    // The Koin graph is verified statically in a test, because the alternative is the first request.
    testImplementation(wip.koin.test)
    testImplementation(libs.ktor.server.testHost)
    // The route tests build their URLs from the same @Resource classes a real client would.
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.ktor.client.resources)
    testImplementation(wip.kotlinx.coroutines.test)
    // The schema-drift guard: Flyway's V1 is hand-written and the tables are petich's, and the only
    // thing that says they agree is a test that asks Exposed what DDL is still missing.
    testImplementation(libs.exposed.migrationJdbc)
    testImplementation(libs.testcontainers.postgresql)
}

// **Pinning a timestamped snapshot pins the root module and not its platform variants**, which is a
// hole in B-13's documented fallback and was found the only way such a thing is: a fix that was
// published, resolved, and still absent from the build.
//
// `io.github.youndie:smtp-client:0.1.0-20260902.062954-3` resolves, and its own Gradle metadata then
// points the JVM variant at `smtp-client-jvm:0.1.0-SNAPSHOT` — the moving coordinate, served from
// whatever the cache last fetched. So the build was compiling yesterday's code against today's
// version number, silently, and `--refresh-dependencies` was the difference between a test failing
// and passing.
//
// This maps the variants onto the same builds. It is a workaround for a library that has released
// nothing; the real fix is a release, and until then this is what makes "pinned" true rather than
// stated. The transport is a build behind the rest on both of smtpkn's publishes — see the catalog.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.github.youndie" &&
            requested.name.startsWith("smtp-") &&
            requested.version == "0.1.0-SNAPSHOT"
        ) {
            val pinned =
                if (requested.name.startsWith("smtp-transport-ktor")) {
                    libs.versions.smtpknTransport.get()
                } else {
                    libs.versions.smtpkn.get()
                }
            useVersion(pinned)
            because("a timestamped snapshot pins the root module only; the platform variants stay -SNAPSHOT")
        }
    }
}
