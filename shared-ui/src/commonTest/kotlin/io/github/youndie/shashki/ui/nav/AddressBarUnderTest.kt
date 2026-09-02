package io.github.youndie.shashki.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The prefix a bundle is served under, and the routes that do not know about it (B-52).
 *
 * **The failure this prevents was live and quiet**: the driver bundle is served at `/driver`, its
 * `Shift` route is `/`, and pushing that put the *rider's* address in the bar — a refresh then landed
 * on the wrong application. It matters more now that the driver signs in, because the redirect URI
 * given to a provider has to be an address this bundle answers.
 */
class AddressBarUnderTest {
    private class Recording(
        var at: String = "/driver",
    ) : AddressBar {
        val pushed = mutableListOf<String>()
        var listener: ((String) -> Unit)? = null

        override fun openedAt(): String = at

        override fun queryAt(): Map<String, String> = mapOf("code" to "abc")

        override fun push(path: String) {
            pushed += path
        }

        override fun onNavigate(listener: (path: String) -> Unit) {
            this.listener = listener
        }
    }

    @Test
    fun `the prefix comes off what is read and goes on what is written`() {
        val browser = Recording(at = "/driver/trip/ride-1")
        val bar = browser.under("/driver")

        assertEquals("/trip/ride-1", bar.openedAt())
        bar.push("/trip/ride-2")
        assertEquals(listOf("/driver/trip/ride-2"), browser.pushed)
    }

    /** The bundle's own root is the prefix itself, not the prefix with a slash hanging off it. */
    @Test
    fun `the root under a prefix is the prefix`() {
        val browser = Recording(at = "/driver")
        val bar = browser.under("/driver")

        assertEquals("/", bar.openedAt())
        bar.push("/")
        assertEquals(listOf("/driver"), browser.pushed)
    }

    /** Back and forward arrive with an address, and it is stripped the same way. */
    @Test
    fun `a browser button reports the route rather than the deployment`() {
        val browser = Recording()
        val bar = browser.under("/driver")
        var arrived: String? = null
        bar.onNavigate { arrived = it }

        browser.listener?.invoke("/driver/trip/ride-3")
        assertEquals("/trip/ride-3", arrived)

        browser.listener?.invoke("/driver")
        assertEquals("/", arrived)
    }

    /** The rider is served at the root, and wrapping in nothing changes nothing. */
    @Test
    fun `an empty prefix is the same bar`() {
        val browser = Recording(at = "/trip/ride-1")

        assertEquals("/trip/ride-1", browser.under("").openedAt())
        assertEquals(mapOf("code" to "abc"), browser.under("/driver").queryAt())
    }
}
