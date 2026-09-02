package io.github.youndie.shashki.server

import io.github.youndie.shashki.server.feature.auth.AuthConfig
import io.ktor.http.ContentType
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.slf4j.LoggerFactory
import java.io.File

/**
 * The two browser bundles, served by the server that answers their requests.
 *
 * **One artefact for a demo, and the trade is named rather than hidden.** The alternative — a static
 * host or a CDN — is what a deployment should do and is what D10's saving needs; serving them here
 * makes the whole product one `docker run`, which is what a reference is for. The cost is real: the
 * two bundles live at different prefixes, so the 3.3 MB Compose runtime they share byte for byte
 * (D10, measured) is fetched **twice** by anybody who opens both. Putting the content-hashed runtime
 * on one path would fix it and is not something this arrangement does.
 *
 * **A bundle that is not there is not an error.** The server's own tests, the saga suite and every
 * `./gradlew run` have no bundles built, and a server that refused to start without them would make
 * the map's tests depend on webpack. The absence is logged once, with the path it looked at.
 *
 * `default("index.html")` is what makes `/trip/abc` work: the routes are the client's and the server
 * has never heard of them, so anything it cannot find under the prefix is the application's own
 * entry point rather than a 404 (B-28).
 */
public fun Route.bundleRoutes(root: File?) {
    if (root == null || !root.isDirectory) {
        LOG.warn(
            "no bundles at {}: the API answers and there is nothing to open in a browser",
            root ?: "(unset ${BundleConfig.ROOT_VARIABLE})",
        )
        return
    }
    for ((prefix, directory) in BundleConfig.BUNDLES) {
        val files = root.resolve(directory)
        if (!files.isDirectory) {
            LOG.warn("no {} bundle at {}", directory, files)
            continue
        }
        staticFiles(prefix, files) { default("index.html") }
        LOG.info("serving the {} bundle at {}", directory, prefix.ifEmpty { "/" })
    }
}

/** Where the bundles are, in the image and on a laptop. */
public object BundleConfig {
    public const val ROOT_VARIABLE: String = "SHASHKI_BUNDLES"

    /**
     * Prefix to directory. **The driver is under a prefix and the rider is at the root**, because a
     * demo is opened by a rider and the driver is the second window somebody arranges deliberately.
     */
    public val BUNDLES: List<Pair<String, String>> = listOf("/driver" to "driver", "" to "rider")

    public fun root(env: (String) -> String? = System::getenv): File? =
        env(ROOT_VARIABLE)?.takeIf { it.isNotBlank() }?.let(::File)
}

private val LOG = LoggerFactory.getLogger("shashki.bundles")

/**
 * The page contract, as one script the browser fetches before the bundle.
 *
 * **The bundle is the same bytes everywhere and the deployment is not.** Where the server is, where
 * the tiles are, which katcher to report to and which build this is — all of it comes from
 * `globalThis.SHASHKI`, which B-28 and B-29 read and nobody wrote. Compiling any of it into the wasm
 * would make every deployment a different artefact, which is the opposite of what a content-hashed
 * bundle is for (D10).
 *
 * Served rather than baked, so the same image runs on a laptop and behind a domain.
 */
public fun Route.configScript(values: Map<String, String>) {
    val body =
        buildString {
            append("globalThis.SHASHKI = ")
            append(values.entries.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": \"${v.escaped()}\"" })
            append(";\n")
        }
    get("/config.js") { call.respondText(body, ContentType.Application.JavaScript) }
}

/** A value from the environment lands inside a JavaScript string literal; it does not get to leave it. */
private fun String.escaped(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c")

/** What a deployment tells the page, read from the environment it was started with. */
public fun pageValues(env: (String) -> String? = System::getenv): Map<String, String> =
    buildMap {
        for ((key, variable) in PAGE_VALUES) env(variable)?.takeIf { it.isNotBlank() }?.let { put(key, it) }
        // The fallback, so a deployment with one address for the provider sets one variable.
        if ("oidcIssuer" !in this) {
            env(AuthConfig.ISSUER_VARIABLE)?.takeIf { it.isNotBlank() }?.let { put("oidcIssuer", it) }
        }
    }

/** Where a *browser* reaches the provider, when that is not where this server reaches it. */
public const val PUBLIC_ISSUER_VARIABLE: String = "SHASHKI_OIDC_PUBLIC_ISSUER"

private val PAGE_VALUES =
    listOf(
        "tilesUrl" to "SHASHKI_TILES_URL",
        "katcherUrl" to "SHASHKI_KATCHER_URL",
        "katcherAppKey" to "SHASHKI_KATCHER_KEY",
        "release" to "SHASHKI_RELEASE",
        // **The provider must have one address, and that is a deployment constraint rather than a
        // simplification.** The first attempt at this gave the server an internal address and the
        // browser an external one, on the theory that each could use what it could reach. It does
        // not work: the validator reads `jwks_uri` out of the *discovery document*, which carries
        // the issuer's own address — so the container fetched discovery from inside the network and
        // was then sent to an address only the browser can reach, and refused every token with 401.
        //
        // `SHASHKI_OIDC_PUBLIC_ISSUER` remains for the case where the two genuinely differ behind a
        // proxy, and falls back to the one the server verifies against (B-41).
        "oidcIssuer" to PUBLIC_ISSUER_VARIABLE,
        "oidcRealm" to AuthConfig.REALM_VARIABLE,
        "oidcClient" to AuthConfig.CLIENT_VARIABLE,
        "driverId" to "SHASHKI_DRIVER_ID",
    )
