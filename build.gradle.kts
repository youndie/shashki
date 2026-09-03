// **No build logic here, and one job that cannot be done anywhere else.** A plugin a module names
// without a version has to already be on the build's plugin classpath, and this block is what puts
// it there. The Kotlin plugins are declared for the same reason: the multiplatform and JVM plugins
// land on the classpath once, so a module asking for a *versioned* one fails with "already on the
// classpath with an unknown version" — a message about neither the plugin nor the module.
//
// What is deliberately absent is shared configuration. Every module states its own target list,
// because research §1.6 found four libraries in this stack whose published targets are not what the
// brief assumed; a target inherited from here would be a target nobody argued for.
plugins {
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    alias(wip.plugins.composeMultiplatform) apply false
    alias(wip.plugins.composeCompiler) apply false
    alias(wip.plugins.ksp) apply false
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.viddik) apply false
}

// **One piece of shared configuration, and the paragraph above is why it needs a reason.**
//
// What that paragraph forbids is a module's own decisions being made here: a target list inherited
// from the root is a target nobody argued for. This is not that. Whether the wasm suite can run is a
// fact about the *machine* — is there a Chrome — and it is the same fact for every module. Five
// copies of it would be five places to forget, which is exactly the shape B-34 was filed to end:
// three items closed against "no browser on the build box" while the switch sat in three build
// scripts.
//
// `scripts/install-chrome.sh` puts a pinned Chrome for Testing on the machine and prints the
// variable to export.
val chrome: Provider<String> = providers.environmentVariable("CHROME_BIN")

subprojects {
    tasks.matching { it.name == "wasmJsBrowserTest" }.configureEach {
        // A checkout on a machine with no browser must still build, and must say what it skipped.
        // Silence here is the failure this whole item exists to end.
        if (!chrome.isPresent) {
            enabled = false
            logger.lifecycle("$path: no CHROME_BIN, so the browser suite is skipped — scripts/install-chrome.sh")
        }

        // **A suite that ran nothing must not pass**, and that half now lives in `sborka.test`.
        //
        // It was written here, in this root build, because the conventions had nowhere to put it —
        // three items had closed against "no browser on the build box" while the suite quietly ran
        // zero tests. sborka 0.2.0 carries it for every non-JVM test task of every repository:
        // `linuxX64Test`, `iosSimulatorArm64Test` and `jsNodeTest` as well as this one. What stays
        // here is the half that is genuinely this repository's — whether the machine has a browser
        // at all.
    }
}
