plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    // **The browser is the target this module exists for; the JVM is here so it can be tested.**
    // There is no browser on the build box, so `wasmJsBrowserTest` cannot run (see the note on the
    // test task below) — the behaviour is pinned on the JVM, where the same `commonMain` code runs
    // against the JDK provider instead of WebCrypto. What that does *not* cover is named in B-09.
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // No test source set for this target and no browser to run one in. `check` compiles it
            // instead, which is the guarantee that matters here: the day this module reaches for a
            // JVM type, the build says so.
            testTask { enabled = false }
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
