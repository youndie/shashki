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
    // **The browser is the product; the desktop target is how it is looked at.** D1 chose Kotlin/Wasm
    // and D10 chose one bundle per role, so `wasmJs` is what ships. `jvm("desktop")` is here because
    // viddik photographs JVM targets only — the same arrangement `:shared-ui` has, for the same
    // reason. Since B-34 the tests run on both: the goldens on desktop, the suite in a browser.
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
            implementation(projects.authClient)
            implementation(projects.crashClient)
            api(projects.protocol)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kvadrant.core)
            // The component model and its vocabulary. The renderers come with `:shared-ui`; what the
            // rider needs is the types, so it can hold a tree and hand it over.
            implementation(libs.kompot.core)
            implementation(libs.kompot.standard)

            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
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
            // The degradation sink is fire-and-forget: nothing in this module reads its answer, so
            // the only way to see what it sends is to catch the request (B-39).
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.mock)
        }
        getByName("wasmJsMain").dependencies {
            implementation(libs.ktor.client.js)
        }
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
            // A real engine for the one test that signs in against a real provider and then calls a
            // real server with what it got.
            implementation(libs.ktor.client.cio)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
            implementation(compose.desktop.currentOs)
        }
    }
}

// **The application's own screens are photographed, and the Screen/Content split is what allows it.**
// `ClassPickerScreen` resolves a view model out of Koin; `ClassPickerContent` takes a state and a
// callback, so a golden of it needs no graph and no server — which is the payoff the split was
// mandated for. What these add over `:shared-ui`'s goldens is the mapping: a `Quote` of 3 890 cents
// becoming `$ 38.90`, and 2 079 seconds becoming `35 min`.
viddik {
    verifyOnCheck = true
}

// The wasm target is compiled by `check`, tests included: a target nobody compiles is a decision
// that quietly stops being true, and the test sources are where a JVM-only idiom would first appear.
tasks.named("check") {
    dependsOn(tasks.named("compileKotlinWasmJs"), tasks.named("compileTestKotlinWasmJs"))
}
