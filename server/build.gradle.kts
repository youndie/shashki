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
    implementation(wip.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.testHost)
}
