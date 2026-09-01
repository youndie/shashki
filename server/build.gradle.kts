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
