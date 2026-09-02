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
    // there is no browser on the build box and viddik photographs JVM targets only — the same
    // arrangement `:shared-ui` already has, for the same reason.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // **No browser on the build box, so this would run nothing and fail loudly about it.**
            // The tests live in `commonTest` and run on the desktop target, which is the same code;
            // what wasm owes the project is that it compiles, and `check` is made to depend on both
            // its compilations below rather than on a suite that cannot start. Running them in a
            // browser needs a browser — the same limit B-09 and B-10 recorded.
            testTask { enabled = false }
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
