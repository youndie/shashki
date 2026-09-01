package io.github.youndie.shashki.server.feature.route.data

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.GHUtility
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.server.pricing.RouteEstimate
import io.github.youndie.shashki.server.pricing.RouteEstimator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.math.roundToInt

/** No road between the two points — off the graph, or on an island the car cannot leave. */
public class NoRouteException(
    message: String,
) : RuntimeException(message)

/**
 * Real roads, from the same OSM extract the tiles are cut from.
 *
 * **Embedded rather than beside.** GraphHopper is a Java library under Apache-2.0, so it runs inside
 * the one Ktor process and a route costs a method call. The alternative — the routing server in its
 * own container — buys nothing here and adds a hop the demo would have to explain.
 *
 * **The first start pays for the import and the rest do not.** `importOrLoad` reads the `.osm.pbf`
 * when [graphDirectory] is empty and memory-maps the prepared graph when it is not. B-06 measured
 * the first case on this city: under four seconds, 98 566 nodes, 13.7 MB on disk. That is small
 * enough that a cold container is not a reason to keep the directory anywhere clever — but it is
 * kept, because four seconds on every restart is four seconds of a demo not answering.
 */
public class GraphHopperRouteEstimator(
    osmFile: Path,
    graphDirectory: Path,
    minNetworkSize: Int = DEFAULT_MIN_NETWORK_SIZE,
) : RouteEstimator,
    AutoCloseable {
    private val hopper: GraphHopper =
        GraphHopper()
            .setOSMFile(osmFile.toString())
            .setGraphHopperLocation(graphDirectory.toString())
            // The three the stock car model reads, named in `car.json`'s own header comment. Getting
            // this list wrong fails at import with the missing name, which is the good case; the bad
            // case would be a silently different model.
            .setEncodedValuesString("car_access, car_average_speed, road_access")
            .setMinNetworkSize(minNetworkSize)
            .setProfiles(Profile(PROFILE).setCustomModel(GHUtility.loadCustomModelFromJar("car.json")))
            .also {
                val started = System.nanoTime()
                it.importOrLoad()
                LOG.info(
                    "graph ready in {} ms from {}",
                    (System.nanoTime() - started) / 1_000_000,
                    graphDirectory,
                )
            }

    override fun estimate(
        from: GeoPoint,
        to: GeoPoint,
    ): RouteEstimate {
        val response = hopper.route(GHRequest(from.lat, from.lon, to.lat, to.lon).setProfile(PROFILE))
        if (response.hasErrors()) {
            // The messages name the point that could not be snapped, which is the only useful thing
            // to say about a failure here — and the reason this is not a bare "no route".
            throw NoRouteException(response.errors.joinToString("; ") { it.message ?: it::class.java.simpleName })
        }
        val best = response.best
        val points = best.points
        return RouteEstimate(
            distanceMetres = best.distance.roundToInt(),
            // GraphHopper answers in milliseconds; every number this project puts on a screen is
            // seconds, and the conversion belongs at the boundary rather than at each reader.
            durationSeconds = (best.time / MILLIS_PER_SECOND).toInt(),
            geometry = List(points.size()) { GeoPoint(points.getLat(it), points.getLon(it)) },
        )
    }

    override fun close(): Unit = hopper.close()

    private companion object {
        val LOG = LoggerFactory.getLogger(GraphHopperRouteEstimator::class.java)!!
        const val PROFILE = "car"
        const val MILLIS_PER_SECOND = 1000L

        /**
         * GraphHopper's own default. It drops connected components smaller than this, which is what
         * keeps a car park that touches no road out of the graph — and what makes a hand-written
         * test fixture of four nodes vanish entirely, so a test that wants one lowers it and says so.
         */
        const val DEFAULT_MIN_NETWORK_SIZE = 200
    }
}
