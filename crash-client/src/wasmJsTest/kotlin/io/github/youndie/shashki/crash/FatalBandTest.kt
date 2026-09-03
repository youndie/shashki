@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.crash

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The band a person sees when a bundle loses an exception (B-56).
 *
 * **Run in a real browser for the same reason `BrowserCrashHookTest` is**: a `@JsFun` is a string
 * the compiler cannot check, and a band that is written and never drawn is exactly the defect this
 * item is about. What the two observed failures had in common was that nothing appeared — so what
 * this asserts is that something does, and that it says which failure it was.
 */
class FatalBandTest {
    @AfterTest
    fun clean() = removeBand()

    @Test
    fun `a synchronous throw puts a band on the page with its message`() {
        installFatalBand()

        fireError("expected 206 for bytes=0-16383, got 404 Not Found")

        assertTrue(bandText().contains("something broke"), "the band has no headline: ${bandText()}")
        assertTrue(bandText().contains("got 404 Not Found"), "the band does not say what failed: ${bandText()}")
    }

    /**
     * **A rejected promise is most of them in a Compose/Wasm application**, and it is the one that
     * painted the driver white: the token exchange the browser refused for want of a CORS header
     * failed after a suspension point and reached nothing at all.
     */
    @Test
    fun `a rejected promise does too`() {
        installFatalBand()

        fireRejection("Fail to fetch")

        assertTrue(bandText().contains("Fail to fetch"), "the band does not name the rejection: ${bandText()}")
    }

    /** Two failures are one band: an application that is losing exceptions must not paper the page. */
    @Test
    fun `a second failure replaces the first band rather than stacking`() {
        installFatalBand()

        fireError("first")
        fireError("second")

        assertEquals(1, bandCount())
        assertTrue(bandText().contains("second"))
    }
}

/*
 * **Both dispatches below blank `window.onerror` and `window.onunhandledrejection` for the length of
 * the event, and put back whatever was there.** Those two properties belong to the test harness, not
 * to the product: karma installs handlers to catch an exception a page loses, and reports one as a
 * failure of whichever test was running — so a test whose entire subject is a lost exception fails
 * for doing its job. It does not stop there either: the Kotlin karma reporter then dies inside
 * `specFailure`, the browser process exits mid-suite, and the modules that were still running report
 * "the test task did not discover any tests", which names neither this file nor the reason. That is
 * what this repository's first CI run said, and it took a local run with `CHROME_BIN` set to find
 * out that only one test was actually failing.
 *
 * The band listens with `addEventListener`, which these lines do not touch, so what is under test
 * still receives exactly the event a browser would deliver.
 */

@JsFun(
    """(message) => {
        const onerror = window.onerror;
        const onrejection = window.onunhandledrejection;
        window.onerror = null;
        window.onunhandledrejection = null;
        try {
            const error = new Error(message);
            window.dispatchEvent(new ErrorEvent('error', { message: message, error: error }));
        } finally {
            window.onerror = onerror;
            window.onunhandledrejection = onrejection;
        }
    }""",
)
private external fun fireError(message: String)

@JsFun(
    """(message) => {
        const onerror = window.onerror;
        const onrejection = window.onunhandledrejection;
        window.onerror = null;
        window.onunhandledrejection = null;
        try {
            const event = new Event('unhandledrejection');
            event.reason = new Error(message);
            window.dispatchEvent(event);
        } finally {
            window.onerror = onerror;
            window.onunhandledrejection = onrejection;
        }
    }""",
)
private external fun fireRejection(message: String)

@JsFun("() => (document.getElementById('shashki-fatal') || { textContent: '' }).textContent")
private external fun bandText(): String

@JsFun("() => document.querySelectorAll('#shashki-fatal').length")
private external fun bandCount(): Int

@JsFun("() => { const b = document.getElementById('shashki-fatal'); if (b) b.remove(); }")
private external fun removeBand()
