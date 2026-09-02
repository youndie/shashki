plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
    // **Needed by the server-driven components, and its absence compiles.** `@Serializable` without
    // this plugin is an annotation with nothing behind it: the class builds, the generated registry
    // builds, and the first decode throws "Serializer for class 'TripRow' is not found". B-17 found
    // it that way round.
    alias(wip.plugins.kotlinSerialization)
    alias(wip.plugins.ksp)
    alias(libs.plugins.viddik)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **Desktop is where the goldens are taken**: viddik's capture engine publishes JVM variants
    // only (research §1.2), so this is the one target a golden can be taken on — and the golden
    // suite is this project's design acceptance, not a side effect of it.
    jvm("desktop")

    // **wasmJs is here to be compiled, and that compilation is D1's evidence.** The brief specifies
    // both clients as Kotlin/Wasm; of the four map routes only route 4 — drawing the tile in Compose
    // ourselves — has anything that can be built for this target at all. Keeping the target in the
    // build means the claim is re-measured by every `check` rather than asserted once: the day
    // something in `map/` reaches for a JVM type, this stops compiling and D1's premise is back
    // under discussion. See research §2 D1.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        // Compose UI's own check wants this before it will let a wasmJs test task exist at all: the
        // Skiko runtime is loaded by webpack, so without an executable binary a Compose UI test
        // would fail for a reason that has nothing to do with the test (CMP-4906). maplibre-compose
        // declares it in its own build for the same sentence.
        binaries.executable()
        // No test source set here, and the empty Karma run would still want a browser on a build
        // box that has none. The goldens are taken on desktop by design (B-02); what this target
        // owes the project is that it compiles, and `check` is made to depend on that below rather
        // than on a suite with nothing in it.
        browser { testTask { enabled = false } }
    }

    sourceSets {
        commonMain.dependencies {
            // The client speaks the wire types rather than copying them: a `GeoPoint` on a map is
            // the same `GeoPoint` the server sent, and a second one would drift within a sprint.
            api(projects.protocol)
            // Named rather than reached through `compose.*`: those accessors are deprecated in
            // Compose 1.12, and they say so only when the build script is compiled — which happens
            // on an empty Gradle home and nowhere else. See B-27.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kvadrant.core)
            // The server-driven subset. B-17 puts the kit's composition rules in the renderer,
            // because only a renderer can decide what happens to a payload a rule forbids.
            implementation(libs.kompot.client)
            api(libs.kompot.core)
            implementation(libs.kompot.registryAnnotations)
        }
        // `getByName` rather than `by getting`: the delegate is deprecated in Gradle 9.6, and its
        // warning is a script-compilation warning like the ones above.
        getByName("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                // The glyph-coverage guard composes every registered fixture and reads its text off
                // the semantics tree; that is the only way to check strings that live as literals
                // inside composables without asking each fixture to declare them.
                implementation(libs.compose.uiTest)
            }
        }
    }
}

// **The goldens are in `check`, and that is a measurement rather than an intention.** viddik leaves
// this off by default because a project that has not bundled a font has host-specific goldens, and
// those redden every machine that did not record them. B-02 asked whether this project is such a
// project and the answer is no: `skeleton_themes`, recorded on macOS, verifies unchanged on Linux.
//
// The measurement is only worth anything because the check was shown to bite. With one extra
// character in a label the same comparison fails at **627 of 329 160 pixels — 0.19 % against a
// 0.05 % tolerance** — and passes again when the character is removed, both under `--rerun-tasks`
// so nothing was cached. Two earlier attempts at that control proved nothing and looked like proof:
// one read the exit status of `tail` through a pipe, the other was silently reverted by the file
// sync before Kotlin compiled.
viddik {
    verifyOnCheck = true
}

// **The wasmJs claim is checked, not stated.** With the browser test task disabled, nothing in
// `check` would otherwise touch this target, and a target nobody compiles is a decision that quietly
// stops being true. D1 rests on `map/` being buildable for Kotlin/Wasm, so `check` compiles it.
// The registry processor, per target: KSP runs once per compilation and the tag keeps the two
// generated files from colliding on a name. `kompotModuleTag` is required — the processor errors
// without it rather than guessing, which is why it is here and not defaulted.
dependencies {
    add("kspDesktop", libs.kompot.registryProcessor)
    add("kspWasmJs", libs.kompot.registryProcessor)
}

ksp { arg("kompotModuleTag", "ShashkiUi") }

tasks.named("check") { dependsOn(tasks.named("compileKotlinWasmJs")) }

// **Every Compose artefact named by hand is checked against the version the plugin applies.**
//
// The accessors would have carried the plugin's own version; they are deprecated in Compose 1.12 and
// say so only when the build script is compiled, which is why this surfaced during B-13's empty-cache
// build and not in five hundred incremental ones. Naming the artefacts trades a warning for numbers
// that can drift, and a Compose UI a minor away from its runtime is the `NoSuchMethodError` research
// §1.2 describes.
//
// **By group rather than by alias**, so an artefact added tomorrow is covered without anybody
// remembering to extend a list here — which is the difference between a guard and an inventory.
run {
    val expected = wip.versions.composeMultiplatform.get()
    val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val named =
        catalog.libraryAliases
            .mapNotNull { alias -> catalog.findLibrary(alias).orElse(null)?.get() }
            .filter { it.module.group.startsWith("org.jetbrains.compose") }

    // The vacuity guard. A check over an empty list passes for ever and reports nothing, and the day
    // somebody puts these back behind an accessor this should say so rather than go quiet.
    check(named.isNotEmpty()) { "no Compose artefact is named by hand any more; this check passes over nothing" }

    val drifted = named.filter { it.versionConstraint.requiredVersion != expected }
    check(drifted.isEmpty()) {
        "Compose is $expected but ${drifted.size} artefact(s) are pinned elsewhere: " +
            drifted.joinToString { "${it.module} at ${it.versionConstraint.requiredVersion}" }
    }
}
