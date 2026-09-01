package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.server.common.haversineMetres
import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import io.github.youndie.shashki.server.feature.route.data.NoRouteException
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The router, on a graph small enough to reason about by hand.
 *
 * The fixture is an L: the road from the west end to the north end goes east and then north, and the
 * straight line between them is the hypotenuse. Every assertion below is about that difference,
 * because "a number came back" is what a broken router also produces.
 */
class GraphHopperRouteEstimatorTest {
    private val estimator = FixtureGraph.estimator

    @Test
    fun `the route follows the road rather than the line between the points`() {
        val route = estimator.estimate(WEST_END, NORTH_END)

        val asTheCrowFlies = haversineMetres(WEST_END, NORTH_END)
        assertTrue(
            route.distanceMetres > asTheCrowFlies * 1.2,
            "routed ${route.distanceMetres} m against ${asTheCrowFlies.toInt()} m straight — this is the hypotenuse, not the road",
        )
        // And the corner is in it: the road turns at the junction, so a point near it is on the way.
        assertTrue(
            route.geometry.any { haversineMetres(it, CORNER) < CORNER_TOLERANCE_METRES },
            "the geometry never passes the junction: ${route.geometry}",
        )
    }

    @Test
    fun `the geometry starts and ends at the requested points, and is a line rather than two`() {
        val route = estimator.estimate(WEST_END, NORTH_END)

        assertTrue(route.geometry.size >= 3, "a straight two-point answer is the stand-in's shape")
        assertTrue(haversineMetres(route.geometry.first(), WEST_END) < SNAP_TOLERANCE_METRES)
        assertTrue(haversineMetres(route.geometry.last(), NORTH_END) < SNAP_TOLERANCE_METRES)
        // The points add up to the distance the router reported, which is what makes it safe to draw
        // one and print the other on the same screen.
        val walked = route.geometry.zipWithNext { a, b -> haversineMetres(a, b) }.sum()
        assertEquals(route.distanceMetres.toDouble(), walked, walked * GEOMETRY_TOLERANCE)
    }

    @Test
    fun `the duration is a driving time rather than the stand-in's constant speed`() {
        val route = estimator.estimate(WEST_END, NORTH_END)
        val standIn = StraightLineRouteEstimator().estimate(WEST_END, NORTH_END)

        assertTrue(route.durationSeconds > 0)
        // Residential roads in the stock car model are slower than the stand-in's 30 km/h city
        // average, and the route is longer as well — so this is not a coincidence of two constants.
        assertTrue(
            route.durationSeconds > standIn.durationSeconds,
            "routed ${route.durationSeconds} s against the stand-in's ${standIn.durationSeconds} s",
        )
    }

    @Test
    fun `a point with no road near it is refused rather than answered`() {
        val outOfRange = GeoPoint(47.5000, 19.0400)

        val failure = assertFailsWith<NoRouteException> { estimator.estimate(WEST_END, outOfRange) }

        assertTrue(failure.message!!.isNotBlank(), "the failure has to name the point that would not snap")
    }

    /**
     * The stand-in reports no geometry at all, and that is checked here rather than left implied:
     * a screen that drew its two points as a road would be drawing a road that is not there.
     */
    @Test
    fun `the stand-in offers no geometry`() {
        assertEquals(emptyList(), StraightLineRouteEstimator().estimate(WEST_END, NORTH_END).geometry)
    }

    private companion object {
        val WEST_END = GeoPoint(46.0500, 14.5000)
        val NORTH_END = GeoPoint(46.0700, 14.5200)
        val CORNER = GeoPoint(46.0500, 14.5200)
        const val CORNER_TOLERANCE_METRES = 50.0
        const val SNAP_TOLERANCE_METRES = 100.0
        const val GEOMETRY_TOLERANCE = 0.02
    }
}
