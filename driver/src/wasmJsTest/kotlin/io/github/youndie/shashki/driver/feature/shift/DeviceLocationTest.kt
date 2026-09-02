package io.github.youndie.shashki.driver.feature.shift

import io.github.youndie.shashki.driver.feature.shift.data.deviceLocation
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * The one thing about B-49 that only a browser can answer: does the interop run?
 *
 * **A `js("…")` block compiles whatever it contains.** The Kotlin compiler checks the signature and
 * not the JavaScript, so "it builds" says nothing about whether `watchPosition` is reachable, whether
 * a Kotlin lambda survives being handed to the browser as a callback, or whether cancelling the flow
 * calls `clearWatch` on something that exists. This runs it in the headless Chrome `check` already
 * uses and asserts the two things that are true of a browser nobody has granted anything:
 * **nothing throws, and no position appears.**
 *
 * The silence is the assertion, and it is the same silence a denied permission produces — which is
 * the case the configured-point fallback exists for and the one every laptop demo takes.
 */
class DeviceLocationTest {
    @Test
    fun theWatchStartsAndProducesNothingWithoutAPermission() =
        runTest {
            val fix = withTimeoutOrNull(2.seconds) { deviceLocation().firstOrNull() }

            assertNull(fix, "a browser that granted nothing produced a position")
        }
}
