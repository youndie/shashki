package io.github.youndie.shashki.server.dispatch

import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.common.haversineMetres
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A driver's last known position, as the index holds it. */
public data class DriverPresence(
    val driverId: String,
    val rideClass: RideClass,
    val rating: Double,
    val at: GeoPoint,
    val reportedAtEpochMs: Long,
)

/**
 * Where the online drivers are, right now.
 *
 * **A cache, not a record, and the difference is the whole design.** Positions arrive on a socket
 * and are written here; nothing is persisted and nothing goes through the broker (research §1.6a).
 * Losing it costs one reporting interval: the drivers still online report again, and the index is
 * whole. That is why there is no repository behind it and no migration for it.
 */
public interface DriverIndex {
    public fun report(
        report: DriverReport,
        nowEpochMs: Long,
    )

    /** The driver's app said goodbye. A crash is covered by [STALE_AFTER_MS] instead. */
    public fun goOffline(driverId: String)

    /**
     * Where one driver was last seen, or `null` if they are offline or their last report is stale.
     *
     * **Added for the rider's trip screen, and by name rather than through [near].** A rider watching
     * a car does not want the nearest driver, they want *theirs*; asking [near] and filtering would
     * be a search of the whole neighbourhood for an answer the index already holds by key — and it
     * would silently return somebody else's car when the right one had gone quiet.
     */
    public fun whereIs(
        driverId: String,
        nowEpochMs: Long,
    ): DriverPresence?

    /** Nearest first, then best rated. Drivers of another class, stale or offline are not here. */
    public fun near(
        point: GeoPoint,
        rideClass: RideClass,
        nowEpochMs: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<DriverCandidate>

    public fun onlineCount(nowEpochMs: Long): Int

    public companion object {
        /**
         * How long a report is worth believing. The item's own sentence — "losing it costs one
         * minute of positions" — read as a number: a driver whose app has been silent longer than
         * this is not offered a ride, because the position that would be offered against is a
         * guess about where they were.
         */
        public const val STALE_AFTER_MS: Long = 60_000

        public const val DEFAULT_LIMIT: Int = 8
    }
}

/**
 * A grid of cells, each holding the drivers last seen inside it.
 *
 * **A latitude/longitude grid rather than a base-32 geohash string, and the difference is
 * deliberate.** What a geohash buys is that a query touches a bounded number of buckets instead of
 * scanning every driver, and a grid keyed by a pair of integers buys exactly that at a fraction of
 * the code. What the *string* additionally buys — a sortable key that a shared store can range-scan
 * — is worth nothing while the index lives in one process's memory, which is where research §1.6a
 * says it lives. If it ever moves into Redis, the string is the change, and this comment is the
 * reason it was not paid for early.
 *
 * The query walks rings outwards from the pickup's cell and stops as soon as a ring is entirely
 * beyond the nearest candidate already found — so a dense city touches one ring and an empty
 * suburb widens until [MAX_RINGS] rather than scanning the map.
 */
public class GridDriverIndex : DriverIndex {
    private val byDriver = ConcurrentHashMap<String, DriverPresence>()
    private val byCell = ConcurrentHashMap<Cell, MutableSet<String>>()

    override fun report(
        report: DriverReport,
        nowEpochMs: Long,
    ) {
        val presence =
            DriverPresence(report.driverId, report.rideClass, report.rating, report.at, nowEpochMs)
        val cell = cellOf(report.at)
        byDriver.put(report.driverId, presence)?.let { previous ->
            val old = cellOf(previous.at)
            if (old != cell) byCell[old]?.remove(report.driverId)
        }
        byCell.computeIfAbsent(cell) { ConcurrentHashMap.newKeySet() }.add(report.driverId)
    }

    override fun whereIs(
        driverId: String,
        nowEpochMs: Long,
    ): DriverPresence? = byDriver[driverId]?.takeIf { nowEpochMs - it.reportedAtEpochMs <= DriverIndex.STALE_AFTER_MS }

    override fun goOffline(driverId: String) {
        byDriver.remove(driverId)?.let { byCell[cellOf(it.at)]?.remove(driverId) }
    }

    override fun near(
        point: GeoPoint,
        rideClass: RideClass,
        nowEpochMs: Long,
        limit: Int,
    ): List<DriverCandidate> {
        val origin = cellOf(point)
        val found = mutableListOf<Pair<DriverPresence, Double>>()
        for (ring in 0..MAX_RINGS) {
            for (cell in ringAround(origin, ring)) {
                for (driverId in byCell[cell].orEmpty()) {
                    val presence = byDriver[driverId] ?: continue
                    if (presence.rideClass != rideClass) continue
                    if (nowEpochMs - presence.reportedAtEpochMs > DriverIndex.STALE_AFTER_MS) continue
                    found += presence to haversineMetres(point, presence.at)
                }
            }
            // A ring is entirely at least `ring * CELL` away, so once the closest hit is nearer than
            // the next ring can possibly be, widening cannot improve the answer.
            val nearest = found.minOfOrNull { it.second }
            if (found.size >= limit && nearest != null && nearest <= ring * cellSizeMetres()) break
        }
        return found
            .sortedWith(compareBy({ it.second }, { -it.first.rating }))
            .take(limit)
            .map { (presence, metres) ->
                DriverCandidate(presence.driverId, metres.roundToInt(), presence.rating, presence.at)
            }
    }

    override fun onlineCount(nowEpochMs: Long): Int =
        byDriver.values.count { nowEpochMs - it.reportedAtEpochMs <= DriverIndex.STALE_AFTER_MS }

    private data class Cell(
        val lat: Int,
        val lon: Int,
    )

    private fun cellOf(p: GeoPoint) = Cell(floor(p.lat / CELL_DEGREES).toInt(), floor(p.lon / CELL_DEGREES).toInt())

    private fun ringAround(
        origin: Cell,
        ring: Int,
    ): List<Cell> =
        if (ring == 0) {
            listOf(origin)
        } else {
            buildList {
                for (dLat in -ring..ring) {
                    for (dLon in -ring..ring) {
                        // Only the perimeter: the inside was covered by the previous rings.
                        if (kotlin.math.abs(dLat) == ring || kotlin.math.abs(dLon) == ring) {
                            add(Cell(origin.lat + dLat, origin.lon + dLon))
                        }
                    }
                }
            }
        }

    /** A cell's smaller side, in metres — the latitude one, which does not vary with longitude. */
    private fun cellSizeMetres(): Double = CELL_DEGREES * METRES_PER_DEGREE_LAT

    private companion object {
        /** About 1.1 km at this latitude: a few city blocks, so a pickup's own ring is usually enough. */
        const val CELL_DEGREES = 0.01

        const val METRES_PER_DEGREE_LAT = 111_320.0

        /** Past this the city is empty and the answer is "no cars nearby" rather than a wider net. */
        val MAX_RINGS = ceil(5_000.0 / (CELL_DEGREES * METRES_PER_DEGREE_LAT)).toInt()
    }
}
