plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    alias(wip.plugins.ksp)
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
            // **The server-driven components live here so that a server can build one** (B-65).
            // `kompot-core` carries the component interface and no Compose, which is the whole
            // reason this is possible: `:server` depends on this module and could never depend on
            // `:shared-ui`. `api` because the components are this module's public surface — a
            // consumer that cannot name `KompotComponent` cannot name what it was sent.
            api(libs.kompot.core)
            implementation(libs.kompot.registryAnnotations)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// **The polymorphic registration for those components is generated here, beside them.**
//
// The renderers stay in `:shared-ui` and are registered by that module's own run of the same
// processor; the two halves meet at the type argument of `KompotComponentRenderer<T>`, which KSP
// resolves across the module boundary. `kompotModuleTag` is required — two modules generating
// `GeneratedKompotRegistration.kt` into one package would collide — and it names this half.
dependencies { add("kspCommonMainMetadata", libs.kompot.registryProcessor) }

ksp { arg("kompotModuleTag", "ShashkiProtocol") }

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
}

// Every task that walks `commonMain` now reads a directory this one writes, and Gradle refuses an
// undeclared read of another task's output. Same wiring as `:shared-ui`, same reason.
tasks
    .matching {
        it.name != "kspCommonMainKotlinMetadata" &&
            (
                it.name.startsWith(
                    "compile",
                ) || it.name.startsWith("ksp") || it.name.contains("ktlint", ignoreCase = true)
            )
    }.configureEach { dependsOn("kspCommonMainKotlinMetadata") }

// Generated code is written by nobody and holding it to a house style produces failures whose fix
// is in another repository. A filter rather than a removed source directory, so ktlint still *sees*
// the file and the task dependency above stays honest.
ktlint {
    filter { exclude { it.file.path.contains("/generated/") } }
}
