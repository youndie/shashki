plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
    alias(wip.plugins.ksp)
    alias(libs.plugins.viddik)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **Desktop only, and it is not a placeholder.** viddik's capture engine publishes JVM variants
    // only (research §1.2), so this is the one target a golden can be taken on — and the golden
    // suite is this project's design acceptance, not a side effect of it. The browser target joins
    // when B-01 says which browser target that is.
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // The client speaks the wire types rather than copying them: a `GeoPoint` on a map is
            // the same `GeoPoint` the server sent, and a second one would drift within a sprint.
            api(projects.protocol)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kvadrant.core)
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
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
