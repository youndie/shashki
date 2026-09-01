plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **Two targets, and B-01 is why there are exactly two.** The server reads this on the JVM; the
    // clients read it in a browser, and D1 settled which browser that is — `wasmJs`, because it is
    // the only one the whole stack can reach at once (research §2 D1). The `js` target that the map
    // library does publish is not added: nothing here would run on it, which is the failure
    // kvadrant-ui's own D14 exists to prevent.
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

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
