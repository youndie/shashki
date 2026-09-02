rootProject.name = "shashki"

// `projects.protocol` instead of `project(":protocol")`: a typo in the second is a runtime failure
// naming a path, in the first it is a compile error naming a symbol.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Written out by hand, and it has to be: `pluginManagement` is evaluated before any settings
        // plugin is applied — including the sborka one, which is fetched through it. The viddik
        // Gradle plugin lives here too, and it is not on the plugin portal either.
        //
        // Filtered, because an unfiltered repository takes part in resolving *every* plugin: it is
        // asked for coordinates it has never held, and the day its host is unreachable Gradle
        // disables it and fails plugins that live elsewhere.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("ru\\.workinprogress.*") }
        }
    }
}

plugins {
    // Lets Gradle fetch the JDK 25 toolchain itself, so the build does not depend on somebody having
    // installed 25 by hand on the machine it runs on.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // mavenCentral() with its content filters, the shared `wip` catalog, and the check that this
    // repository's `.editorconfig` is the one the rest of them use.
    id("ru.workinprogress.sborka.settings") version "0.1.0.23"
}

dependencyResolutionManagement {
    repositories {
        // kvadrant-ui and viddik are both on this host and under **different groups** — kvadrant
        // publishes as `io.github.youndie`, viddik as `ru.workinprogress` — so the conventions'
        // own declaration, which filters to the latter, does not reach kvadrant. Two filters rather
        // than one unfiltered repository, for the reason spelled out above.
        //
        // `/snapshots` and not `/releases` is where kvadrant-core 0.1.0 actually is; checked rather
        // than assumed, because `/releases/io/github/youndie` is a 404.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content {
                includeGroupByRegex("ru\\.workinprogress.*")
                includeGroupByRegex("io\\.github\\.youndie.*")
            }
        }
        // Navigation 3's runtime half is published by Google and not mirrored to Maven Central —
        // `androidx.navigation3:navigation3-runtime` is a 404 there. Filtered like the others: an
        // unfiltered repository takes part in resolving every dependency.
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
    }
}

// The protocol both halves read, the server that speaks it, and the rider that speaks to it.
//
// The clients waited on B-01 deliberately — a module whose target list is a guess is a module that
// gets rewritten — and D1 answered it: `wasmJs`, with a desktop target beside it because viddik
// photographs JVM targets and a screen nobody can see is a screen nobody can review. The driver
// client is not here; it is the second bundle D10 chose and nothing in the backlog schedules it yet.
include(":protocol")
include(":server")
include(":shared-ui")
// The browser half of sign-in. Not in :protocol, which is the contract with *this* server — the
// identity provider is a different service with its own wire format. See B-09.
include(":auth-client")
// The browser half of crash reporting. Separate from :auth-client because it needs an HTTP client
// and that module deliberately has none. See B-10.
include(":crash-client")
// The rider application: the shell every other module's work hangs in. See B-28.
include(":rider")
