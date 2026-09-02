package io.github.youndie.shashki.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bundles are served, and the API still wins.
 *
 * **The failure this exists for is the whole API answering with a web page.** `default("index.html")`
 * at the root answers any path it is given, so a static route mounted there is a wildcard over the
 * entire server. Ktor matches by specificity rather than by declaration order, which means it works
 * — and "it works because of a routing rule nobody wrote down" is exactly the kind of thing that
 * stops working in a minor version.
 */
class BundleRoutingTest {
    @Test
    fun `the bundles answer at their prefixes and unknown paths under them are the entry point`() =
        testApplication {
            val root = bundles()
            application {
                baseModule()
                routing { bundleRoutes(root) }
            }

            assertEquals("rider index", client.get("/").bodyAsText())
            assertEquals("driver index", client.get("/driver").bodyAsText())
            // A client route the server has never heard of: `/trip/abc` is the rider's own (B-28).
            assertEquals("rider index", client.get("/trip/ride-1").bodyAsText())
            assertEquals("driver index", client.get("/driver/trip/ride-1").bodyAsText())
        }

    /** The one that matters: a real route is more specific than a wildcard and answers first. */
    @Test
    fun `an API route still answers with the bundles mounted over it`() =
        testApplication {
            val root = bundles()
            application {
                baseModule()
                routing { bundleRoutes(root) }
            }

            val health = client.get("/health")

            assertEquals(HttpStatusCode.OK, health.status)
            assertEquals("ok", health.bodyAsText(), "the static fallback swallowed the API")
        }

    /**
     * **A server with no bundles is a running server**, which every test in this suite and every
     * `./gradlew run` depends on: the API answers and there is simply nothing to open.
     */
    @Test
    fun `no bundles is not a failure`() =
        testApplication {
            application {
                baseModule()
                routing { bundleRoutes(null) }
            }

            assertEquals(HttpStatusCode.OK, client.get("/health").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        }

    /**
     * The page contract, which nothing wrote until B-35: both bundles read `globalThis.SHASHKI` and
     * no page had ever set it — because no page existed.
     */
    @Test
    fun `the config script sets what the bundles read`() =
        testApplication {
            application {
                baseModule()
                routing { configScript(mapOf("tilesUrl" to "https://tiles.example/city.pmtiles")) }
            }

            val script = client.get("/config.js").bodyAsText()

            assertEquals("globalThis.SHASHKI = {\"tilesUrl\": \"https://tiles.example/city.pmtiles\"};\n", script)
        }

    /**
     * **A value from the environment lands inside a string literal and does not get to leave it.**
     * Whoever writes these variables writes the page, and a deployment that pastes a quote into one
     * would otherwise be running its own JavaScript in every visitor's browser.
     */
    @Test
    fun `a value cannot break out of the script`() =
        testApplication {
            application {
                baseModule()
                routing { configScript(mapOf("release" to """a"; alert(1); //<script>""")) }
            }

            val script = client.get("/config.js").bodyAsText()

            // Asserted as the exact escaped form rather than as the absence of a substring: the
            // escaped text still *contains* `"; alert`, so "does not contain" was the wrong
            // question and passed for the wrong reason in the other direction.
            assertEquals(
                """globalThis.SHASHKI = {"release": "a\"; alert(1); //\u003cscript>"};""" + "\n",
                script,
            )
        }

    /** Two directories with an index each, which is what the image lays out. */
    private fun bundles(): File {
        val root = Files.createTempDirectory("shashki-bundles").toFile()
        for ((directory, text) in listOf("rider" to "rider index", "driver" to "driver index")) {
            val into = root.resolve(directory)
            assertTrue(into.mkdirs())
            into.resolve("index.html").writeText(text)
        }
        root.deleteOnExit()
        return root
    }
}
