plugins {
    alias(wip.plugins.kotlinJvm)
    alias(wip.plugins.kotlinSerialization)
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.lint")
    application
}

application {
    mainClass.set("io.github.youndie.shashki.server.ApplicationKt")
}

// One process, and the brief says why: the "microservice" story here is told by the saga and the
// broker, not by ten deployments. The boundaries the research committed to — rider-api, driver-api,
// dispatch, pricing, billing — are packages until there is enough code for a Gradle module to mean
// something; splitting an empty server into five of them would be inventing boundaries before the
// domain has any.
dependencies {
    implementation(projects.protocol)

    implementation(platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.resources)
    // Driver positions arrive on a socket and go straight into the geo-index — never into the
    // broker (research §1.6a). The client half is here because the simulator is a client of that
    // same socket rather than a back door into the index.
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.client.resources)
    implementation(libs.ktor.client.cio)
    implementation(wip.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

    // The order saga: the engine, its Postgres repository, and the outbox relay. petich-postgres
    // ships no driver, no pool and no DDL on purpose — those three are the application's, below.
    // Token verification. The provider is shildik and the validator is shildik's — a second
    // implementation of "is this signature ours" is the last thing a service should own.
    implementation(libs.shildik.oidcAuthServer)
    implementation(libs.shildik.oidcAuthCore)

    // The one screen the server owns, as a tree. `kompot-core` is the component model and
    // `kompot-standard` its vocabulary — neither carries Compose, which is what lets a headless
    // server build a screen at all. See B-32 and research §2 D11.
    implementation(libs.kompot.core)
    implementation(libs.kompot.standard)

    // The receipt, over SMTP written from the RFC rather than through a JVM mail library — which is
    // the only thing this part of the demo demonstrates. See B-14 and research §1.6d.
    implementation(libs.smtpkn.client)
    implementation(libs.smtpkn.transportKtor)
    implementation(libs.smtpkn.tlsJvm)
    implementation(libs.smtpkn.mime)
    implementation(libs.smtpkn.sasl)

    // Routing on the city's own extract, in this process. See B-23 and `GraphHopperRouteEstimator`.
    implementation(libs.graphhopper.core)

    implementation(libs.petich.core)
    implementation(libs.petich.postgres)
    implementation(libs.petich.outboxCore)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(platform(wip.koin.bom))
    implementation(wip.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    testImplementation(kotlin("test"))
    // The Koin graph is verified statically in a test, because the alternative is the first request.
    testImplementation(wip.koin.test)
    testImplementation(libs.ktor.server.testHost)
    // The route tests build their URLs from the same @Resource classes a real client would.
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.ktor.client.resources)
    testImplementation(wip.kotlinx.coroutines.test)
    // The schema-drift guard: Flyway's V1 is hand-written and the tables are petich's, and the only
    // thing that says they agree is a test that asks Exposed what DDL is still missing.
    testImplementation(libs.exposed.migrationJdbc)
    testImplementation(libs.testcontainers.postgresql)

    // `ProtectedRidesTest` signs in the way the rider application does. The point of that test is
    // that the two halves agree, so the client half has to be *this* client and not a re-enactment
    // of it — a hand-rolled token request in a test proves the test can talk to shildik.
    testImplementation(projects.authClient)
    testImplementation(libs.ktor.client.cio)
}

// **Pinning a timestamped snapshot pins the root module and not its platform variants**, which is a
// hole in B-13's documented fallback and was found the only way such a thing is: a fix that was
// published, resolved, and still absent from the build.
//
// `io.github.youndie:smtp-client:0.1.0-20260902.062954-3` resolves, and its own Gradle metadata then
// points the JVM variant at `smtp-client-jvm:0.1.0-SNAPSHOT` — the moving coordinate, served from
// whatever the cache last fetched. So the build was compiling yesterday's code against today's
// version number, silently, and `--refresh-dependencies` was the difference between a test failing
// and passing.
//
// This maps the variants onto the same builds. It is a workaround for a library that has released
// nothing; the real fix is a release, and until then this is what makes "pinned" true rather than
// stated. The transport is a build behind the rest on both of smtpkn's publishes — see the catalog.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.github.youndie" &&
            requested.name.startsWith("smtp-") &&
            requested.version == "0.1.0-SNAPSHOT"
        ) {
            val pinned =
                if (requested.name.startsWith("smtp-transport-ktor")) {
                    libs.versions.smtpknTransport.get()
                } else {
                    libs.versions.smtpkn.get()
                }
            useVersion(pinned)
            because("a timestamped snapshot pins the root module only; the platform variants stay -SNAPSHOT")
        }
    }
}

// **The image, and the three things that have to be in it besides the jars** (B-35).
//
// A `Sync` into a staging directory rather than a build context of the whole repository: what goes
// into an image should be a list somebody wrote, not whatever happened to be in the tree.
//
// Everything here is resolved to a plain value at configuration time. A `doFirst` that reached back
// for `file(...)` or a provider is a script-object reference, which the configuration cache refuses
// to serialise — and the message it gives names the type rather than the line.
val imageContext = layout.buildDirectory.dir("image")
val preparedGraph: File =
    layout.buildDirectory
        .dir("graph")
        .get()
        .asFile

/**
 * The city, as an extract.
 *
 * **One input, and the graph is made from it here.** The first version accepted a prepared graph
 * directory instead, which is faster and is a landmine: GraphHopper stores a hash of the profile
 * beside the data and refuses a graph built by a different configuration, so an image baked from
 * whatever graph a build machine had lying around dies on start with `Profiles do not match`. It
 * did. Three seconds of import is the price of the artefact being made of one thing.
 */
val osmExtract: File? =
    providers
        .gradleProperty("osmFile")
        .orElse(providers.environmentVariable("SHASHKI_OSM_FILE"))
        .orNull
        ?.let(::File)

/**
 * The tag, which names a commit.
 *
 * **Passed in rather than read from git**, because the machine this builds on is a mutagen replica
 * with no `.git` in it — `git rev-parse` there answers about nothing. CI and the wrapper pass
 * `-PcommitSha`; a local build without one gets a `dev-` tag, which is honest about being unreadable
 * rather than pretending to be `latest`.
 */
val imageTag: String =
    providers
        .gradleProperty("commitSha")
        .orElse(providers.environmentVariable("SHASHKI_COMMIT"))
        .orNull
        ?.let { "shashki/server:$it" }
        ?: "shashki/server:dev"

val prepareGraph =
    tasks.register<JavaExec>("prepareGraph") {
        group = "distribution"
        description = "Imports the OSM extract into a road graph, with the profile this server reads it back with."

        // Checked at configuration time: an image task that fails after five minutes of webpack, for
        // a file it could have looked at first, is a task that wastes an afternoon.
        require(osmExtract != null) {
            "no extract: pass -PosmFile=<city.osm.pbf> or set SHASHKI_OSM_FILE. map/city_tiles.sh builds one. " +
                "An image of this server without a map has a fare and a wait that are wrong and still look " +
                "like numbers (B-23), so this refuses rather than falling back."
        }
        require(osmExtract.isFile) { "$osmExtract is not a file" }

        mainClass.set("io.github.youndie.shashki.server.feature.route.PrepareGraphKt")
        classpath = sourceSets.main.get().runtimeClasspath
        args(osmExtract.absolutePath, preparedGraph.absolutePath)

        inputs.file(osmExtract)
        outputs.dir(preparedGraph)
        // GraphHopper loads rather than imports when the directory is not empty, so a stale graph
        // would survive a change to the profile — which is the failure this whole task exists for.
        // A plain `File` captured by value: a `doFirst` holding a provider from `layout` is a
        // script-object reference, and the configuration cache refuses it with a message about the
        // task's type rather than about this line.
        val graph = preparedGraph
        doFirst { graph.deleteRecursively() }
    }

val assembleImageContext =
    tasks.register<Sync>("imageContext") {
        group = "distribution"
        description = "Assembles what the image is built from: the application, the graph, both bundles."

        into(imageContext)
        // `installDist` sets its own modes and the launcher has to stay executable, so this one is
        // copied as it is.
        from(tasks.named("installDist")) { into("app") }

        // **The content is given a mode, because a file's mode in the repository reaches the image.**
        // `index.html` was written with a restrictive umask, arrived as `600 root`, and the container
        // — running as 1000 — answered `GET /` with **200 and an empty body**: Ktor sizes the
        // response from the file it can stat and then cannot read it. A browser told "964 bytes" and
        // given none simply stops, so the symptom was a blank window and a log nobody was reading.
        //
        // Per spec and **not on the task**, which was the second version and was worse: a blanket
        // `filePermissions` took the execute bit off `bin/server`, and the container failed to start
        // with `exec: permission denied`. Only the copied content needs a mode; `installDist`
        // already gives its own the right ones.
        from(project(":rider").tasks.named("wasmJsBrowserDistribution")) {
            into("bundles/rider")
            readable()
        }
        from(project(":driver").tasks.named("wasmJsBrowserDistribution")) {
            into("bundles/driver")
            readable()
        }
        from(prepareGraph) {
            into("graph")
            readable()
        }
    }

/** Readable by anybody, which is what a file the server only serves needs to be. */
fun CopySpec.readable() {
    filePermissions { unix("rw-r--r--") }
    dirPermissions { unix("rwxr-xr-x") }
}

tasks.register<Exec>("image") {
    group = "distribution"
    description = "Builds the server image, with the road graph and both browser bundles in it."
    dependsOn(assembleImageContext)

    val tag = imageTag
    val dockerfile = rootProject.file("docker/Dockerfile").absolutePath
    val context = imageContext.get().asFile.absolutePath
    commandLine("docker", "build", "--tag", tag, "--file", dockerfile, context)
    doLast { logger.lifecycle("built $tag") }
}
