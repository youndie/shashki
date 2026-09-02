plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // The browser is what this exists for; the JVM is where its behaviour is pinned. katcher's own
    // client covers the JVM and every native target and **not** the browser (research §1.6b), which
    // is the whole reason this module is here rather than a dependency.
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // No browser on the build box, so nothing would run here; `check` compiles the target
            // instead, which is the claim that matters — see the task wiring below.
            testTask { enabled = false }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // **`ktor-client-core` and nothing else.** No engine: the application already has one and
            // a second would be a second connection pool. No `ContentNegotiation` either — the body
            // is serialised here and sent as a string, so installing a plugin is not a hidden part
            // of this module's contract.
            // The BOM, like `:server`: the catalog's Ktor entries carry no version because the
            // version is one number for the whole stack and lives in the shared catalog.
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.core)
            implementation(wip.kotlinx.serialization.json)
            implementation(wip.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            // A real engine for the one test that talks to a real katcher; the rest use MockEngine.
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.mock)
            implementation(wip.kotlinx.coroutines.test)
        }
    }
}

tasks.named("check") { dependsOn(tasks.named("compileKotlinWasmJs")) }
