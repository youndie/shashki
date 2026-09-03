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
        // **All eight, because three of seven is how `/trips` got out.** This list used to name
        // `ClassPicker`, `Callback` and `Trip`; `History` was one of the four nobody added, so R9's
        // address went out into the address bar and came back `null` — a refresh on the rider's own
        // trips landed on the class picker with the URL rewritten to `/`. Found by opening the
        // product, not here.
        //
        // Asking the sealed serializer for its subclasses would make this self-maintaining and does
        // not work: the descriptor of a sealed root is `type` + `value`, and the `value` element is
        // CONTEXTUAL rather than a closed set on this version. So the list is by hand, and the rule
        // is written where it can be read: **a route added to `RiderRoute` is added here.**
        val routes =
            listOf(
                RiderRoute.ClassPicker,
                RiderRoute.Callback,
                RiderRoute.History,
                RiderRoute.Promo,
                RiderRoute.Trip("ride-7"),
                RiderRoute.Matching("ride-7"),
                RiderRoute.Finished("ride-7"),
                RiderRoute.Receipt("ride-7"),
            )

        for (route in routes) {
            assertEquals(route, RiderRoute.ofPath(route.path), "${route.path} did not come back as itself")
        }
    }

    @Test
    fun `the addresses are the ones somebody would paste`() {
        assertEquals("/", RiderRoute.ClassPicker.path)
        // The provider redirects here, so this string is registered in shildik as well as written
        // here — the one address in this application that two systems have to agree about.
        assertEquals("/callback", RiderRoute.Callback.path)
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
