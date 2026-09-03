package io.github.youndie.shashki.driver.feature.trip

import io.github.youndie.shashki.driver.feature.trip.ui.asWord
import io.github.youndie.shashki.protocol.RideStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The driver's screen says words, and every state has one (B-68).
 *
 * **Three of four read as prose by luck.** The header printed the enum's own name, so `assigned`,
 * `arriving` and `arrived` looked deliberate while `in_progress` arrived with an underscore on a
 * screen whose headings are lower-case prose. What this holds is not the four strings — those are a
 * design decision and may change — but that **no state reaches a person as an identifier**, which
 * is the thing the next state added to the wire will otherwise do.
 */
class EveryStateReadsAsAWordTest {
    @Test
    fun `no state reaches the screen as an identifier`() {
        for (status in RideStatus.entries) {
            assertFalse(
                '_' in status.asWord(),
                "${status.name} is drawn as an identifier: ${status.asWord()}",
            )
        }
    }

    /** And the one that was wrong is the one worth naming. */
    @Test
    fun `in progress is a sentence`() {
        assertEquals("on the trip", RideStatus.IN_PROGRESS.asWord())
    }
}
