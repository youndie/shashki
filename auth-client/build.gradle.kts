plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **The browser is the target this module exists for, and since B-34 it is the target the suite
    // runs on.** The same `commonTest` executes twice: on the JVM against the JDK provider, and in a
    // real Chrome against WebCrypto. Until there was a browser only the first happened, so "the
    // challenge the provider verifies is the challenge we compute" was true of a provider the
    // product does not use.
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // **The browser suite is enabled and guarded in the root build**, because the decision
            // is not this module's: it is whether the machine has a Chrome at all. See B-34.
        }
    }

    sourceSets {
        commonMain.dependencies {
            // **The same library shildik verifies with.** Its `Pkce` is the verifying half —
            // `S256(verifier) == challenge`, in constant time — and there is no generating half
            // anywhere in shildik, because generating is the client's job. Using the same primitive
            // is what makes "the two halves agree" a property of one algorithm rather than of two
            // implementations that happen to match today.
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.random)
            implementation(wip.kotlinx.serialization.json)
            // **The endpoint as a type, not a string.** shildik grew a browser target for this in
            // youndie/shildik#20; before it, this module assembled `/realms/{realm}/oauth2/authorize`
            // out of pieces, which is the one thing `@Resource` exists to prevent.
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.resources)
            implementation(libs.shildik.sharedOidc)
            // **The exchange moved here from `:rider` when the driver needed the same session**
            // (B-52). Two bundles signing in two ways is two answers to "am I signed in", and the
            // second one is always the one nobody tests.
            implementation(libs.ktor.client.core)
        }
        jvmTest.dependencies {
            // A real engine for the one test that talks to a real shildik.
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(wip.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        wasmJsMain.dependencies {
            // SHA-256 in a browser is WebCrypto and WebCrypto is asynchronous. That is why
            // `challenge` is a suspend function in `commonMain` rather than a plain one with an
            // awkward actual — the asynchrony is the browser's and it shapes the API for everyone.
            implementation(libs.cryptography.provider.webcrypto)
        }
    }
}

tasks.named("check") { dependsOn(tasks.named("compileKotlinWasmJs")) }
