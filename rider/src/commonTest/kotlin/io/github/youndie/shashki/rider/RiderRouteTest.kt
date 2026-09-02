package io.github.youndie.shashki.rider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The address bar is part of the interface, so the mapping is a contract and gets a test.**
 *
 * A pasted `/trip/abc` has to open that ride after a refactor that renames the class, and an address
 * this application has no screen for has to be a shrug rather than a crash — somebody else's link is
 * not this application's bug.
 */
class RiderRouteTest {
    @Test
    fun `every route survives a round trip through its own address`() {
        val routes = listOf(RiderRoute.ClassPicker, RiderRoute.SignIn, RiderRoute.Trip("ride-7"))

        for (route in routes) {
            assertEquals(route, RiderRoute.ofPath(route.path), "${route.path} did not come back as itself")
        }
    }

    @Test
    fun `the addresses are the ones somebody would paste`() {
        assertEquals("/", RiderRoute.ClassPicker.path)
        assertEquals("/sign-in", RiderRoute.SignIn.path)
        assertEquals("/trip/ride-7", RiderRoute.Trip("ride-7").path)
    }

    @Test
    fun `an empty path is the start, because that is what a bare origin gives`() {
        assertEquals(RiderRoute.ClassPicker, RiderRoute.ofPath(""))
        assertEquals(RiderRoute.ClassPicker, RiderRoute.ofPath("/"))
    }

    /** Not an exception: the application ignores it and stays where it is. */
    @Test
    fun `an address this application has no screen for is nothing, not a failure`() {
        assertNull(RiderRoute.ofPath("/somebody-elses-page"))
        assertNull(RiderRoute.ofPath("/trip/"), "a trip with no id is not a trip")
        assertNull(RiderRoute.ofPath("/trip"))
    }
}
