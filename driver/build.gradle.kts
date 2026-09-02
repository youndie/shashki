plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
    alias(wip.plugins.kotlinSerialization)
    alias(wip.plugins.ksp)
    alias(libs.plugins.viddik)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // The same two targets as `:rider`, for the same two reasons: `wasmJs` is what ships (D1), and
    // `jvm("desktop")` is the only target viddik can photograph.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // **The browser suite is enabled and guarded in the root build**, because the decision
            // is not this module's: it is whether the machine has a Chrome at all. See B-34.
        }
        binaries.executable()
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(projects.sharedUi)
            implementation(projects.crashClient)
            api(projects.protocol)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kvadrant.core)
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            // The position stream is a socket, which is the one thing the rider bundle never needed.
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.resources)
            implementation(libs.ktor.serialization.json)

            implementation(project.dependencies.platform(wip.koin.bom))
            implementation(wip.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.koin.composeNavigation3)
            implementation(libs.navigation3.ui)

            implementation(wip.kotlinx.coroutines.core)
            implementation(wip.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(wip.kotlinx.coroutines.test)
        }
        getByName("wasmJsMain").dependencies {
            implementation(libs.ktor.client.js)
        }
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
            implementation(compose.desktop.currentOs)
            // **The Main dispatcher, without which no view model can be constructed.**
            // `viewModelScope` is `Dispatchers.Main.immediate`; on the JVM that dispatcher arrives
            // through a service loader, and Compose Desktop does not bring it. See the rider's copy.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// **The driver's own screens are photographed, and the Screen/Content split is what allows it.**
// `ShiftContent` takes a state and a callback, so a golden of the offer — the one screen in this
// product with a deadline on it — needs neither a socket nor a server.
viddik {
    verifyOnCheck = true
}

// The wasm target is compiled by `check`, tests included: a target nobody compiles is a decision
// that quietly stops being true, and the test sources are where a JVM-only idiom would first appear.
tasks.named("check") {
    dependsOn(tasks.named("compileKotlinWasmJs"), tasks.named("compileTestKotlinWasmJs"))
}
