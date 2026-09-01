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
    }
}

// The protocol both halves read, and the server that speaks it. The clients are not here yet: their
// target is what B-01 decides, and a module whose target list is a guess is a module that gets
// rewritten. :shared-ui is, because its desktop target is the one viddik can photograph and that
// does not depend on B-01 at all.
include(":protocol")
include(":server")
include(":shared-ui")
