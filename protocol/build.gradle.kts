plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **One target, and that is the point of the comment.** The server reads this on the JVM. The
    // clients will read it in a browser, and which browser target that is — `wasmJs` or `js` — is
    // exactly what B-01 has not decided: research §1.3 found the map library publishes no `wasmJs`
    // variant, and two of this stack's own libraries publish no `js` one. Declaring both now would
    // put a target in the build that nothing runs on, which is the failure kvadrant-ui's own D14
    // exists to prevent.
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(wip.kotlinx.serialization.json)
            implementation(wip.kotlinx.datetime)
            // `@Resource` routes live here so the path to an endpoint exists as a string on neither
            // side: the server matches the class, the client builds the URL from it, and a renamed
            // route is a compile error rather than a 404 in production.
            api("io.ktor:ktor-resources:${wip.versions.ktor.get()}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
