package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The candidate query's rules, where they can be stated exactly: known drivers at known places. */
class DriverIndexTest {
    private val index = GridDriverIndex()
    private val pickup = GeoPoint(46.0511, 14.5051)
    private var now = 1_000_000L

    @Test
    fun `nearest first, and better rated wins a tie`() {
        park("far", metresNorth = 2_000.0, rating = 5.0)
        park("near-poor", metresNorth = 300.0, rating = 4.5)
        park("near-good", metresNorth = 300.0, rating = 4.9)

        val candidates = index.near(pickup, RideClass.COMFORT, now)

        assertEquals(listOf("near-good", "near-poor", "far"), candidates.map { it.driverId })
        assertTrue(candidates.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres })
    }

    @Test
    fun `another class is not a candidate, however close`() {
        park("economy-next-door", metresNorth = 50.0, rideClass = RideClass.ECONOMY)
        park("comfort-far", metresNorth = 4_000.0, rideClass = RideClass.COMFORT)

        assertEquals(listOf("comfort-far"), index.near(pickup, RideClass.COMFORT, now).map { it.driverId })
    }

    @Test
    fun `a driver whose app went quiet stops being offered rides`() {
        park("chatty", metresNorth = 100.0)
        park("silent", metresNorth = 90.0)

        now += DriverIndex.STALE_AFTER_MS + 1
        park("chatty", metresNorth = 100.0)

        assertEquals(listOf("chatty"), index.near(pickup, RideClass.COMFORT, now).map { it.driverId })
        assertEquals(1, index.onlineCount(now))
    }

    @Test
    fun `going offline removes the driver, and moving does not leave a ghost behind`() {
        park("mover", metresNorth = 100.0)
        park("mover", metresNorth = 3_000.0)

        assertEquals(1, index.near(pickup, RideClass.COMFORT, now).size, "one driver, not two entries")
        assertTrue(index.near(pickup, RideClass.COMFORT, now).single().distanceMetres > 2_000)

        index.goOffline("mover")
        assertEquals(emptyList(), index.near(pickup, RideClass.COMFORT, now))
    }

    @Test
    fun `a driver beyond the search radius is not found, so the answer is no cars rather than a wider net`() {
        park("another-town", metresNorth = 40_000.0)

        assertEquals(emptyList(), index.near(pickup, RideClass.COMFORT, now))
    }

    @Test
    fun `a fresh index serves nobody until the stream refills it`() {
        park("someone", metresNorth = 200.0)
        assertEquals(1, index.near(pickup, RideClass.COMFORT, now).size)

        // What a restart leaves: a new process with an empty index and no store to read from.
        val afterRestart = GridDriverIndex()
        assertEquals(
            emptyList(),
            afterRestart.near(pickup, RideClass.COMFORT, now),
            "no candidate is served from a stale process",
        )

        // One reporting interval later the drivers who are still online have said so again.
        afterRestart.report(DriverReport("someone", RideClass.COMFORT, 4.8, north(200.0)), now)
        assertEquals(listOf("someone"), afterRestart.near(pickup, RideClass.COMFORT, now).map { it.driverId })
    }

    private fun park(
        driverId: String,
        metresNorth: Double,
        rating: Double = 4.8,
        rideClass: RideClass = RideClass.COMFORT,
    ) = index.report(DriverReport(driverId, rideClass, rating, north(metresNorth)), now)

    private fun north(metres: Double) = GeoPoint(pickup.lat + metres / METRES_PER_DEGREE_LAT, pickup.lon)

    private companion object {
        const val METRES_PER_DEGREE_LAT = 111_320.0
    }
}
