package io.github.youndie.shashki.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The address bar is part of the interface, so the mapping is a contract and gets a test** — the
 * rider's argument, applied to the second bundle rather than restated in it.
 */
class DriverRouteTest {
    @Test
    fun `every route survives a round trip through its own address`() {
        val routes = listOf(DriverRoute.Shift, DriverRoute.Trip("ride-7"))

        for (route in routes) {
            assertEquals(route, DriverRoute.ofPath(route.path), "${route.path} did not come back as itself")
        }
    }

    @Test
    fun `the addresses are the ones somebody would paste`() {
        assertEquals("/", DriverRoute.Shift.path)
        assertEquals("/trip/ride-7", DriverRoute.Trip("ride-7").path)
    }

    @Test
    fun `an empty path is the shift, because that is what a bare origin gives`() {
        assertEquals(DriverRoute.Shift, DriverRoute.ofPath(""))
        assertEquals(DriverRoute.Shift, DriverRoute.ofPath("/"))
    }

    @Test
    fun `an address this application has no screen for is nothing, not a failure`() {
        assertNull(DriverRoute.ofPath("/somebody-elses-page"))
        assertNull(DriverRoute.ofPath("/trip/"))
    }

    /**
     * **There is deliberately no address for an offer**, and this is where that decision is written
     * down as a test rather than only as a comment: a link to a thing that lives fifteen seconds is
     * broken by design, so the shift screen owns the offer as a state.
     */
    @Test
    fun `an offer has no address of its own`() {
        assertNull(DriverRoute.ofPath("/offer/ride-7"))
    }
}
