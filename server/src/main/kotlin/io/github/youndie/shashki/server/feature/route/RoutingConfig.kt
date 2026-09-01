package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Where the city's extract is, and what happens when it is not there.
 *
 * **The fallback is loud on purpose.** A server that quietly answers with great-circle distances
 * when the extract is missing is a server whose ETAs are wrong in a way nobody notices — the numbers
 * still look like numbers. So the choice is logged at `warn` with the path it looked at, and the
 * message says what the consequence is rather than that a file was absent.
 */
public data class RoutingConfig(
    val osmFile: Path,
    val graphDirectory: Path,
) {
    public companion object {
        public const val OSM_FILE_VARIABLE: String = "SHASHKI_OSM_FILE"
        public const val GRAPH_DIRECTORY_VARIABLE: String = "SHASHKI_GRAPH_DIR"

        private val LOG = LoggerFactory.getLogger(RoutingConfig::class.java)

        /** The config, or `null` when no usable extract is configured. */
        public fun fromEnv(env: (String) -> String? = System::getenv): RoutingConfig? {
            val osm = env(OSM_FILE_VARIABLE)?.let(Path::of) ?: return null
            val graph = env(GRAPH_DIRECTORY_VARIABLE)?.let(Path::of) ?: osm.resolveSibling("graph-cache")
            return if (osm.exists()) RoutingConfig(osm, graph) else null
        }

        /**
         * The binding `rideModule` uses. Separate from [fromEnv] so the decision and the reason for
         * it are in one place rather than spread between a config object and a DI lambda.
         */
        public fun estimator(config: RoutingConfig? = fromEnv()): RouteEstimator =
            if (config == null) {
                LOG.warn(
                    "no OSM extract at ${'$'}{OSM_FILE_VARIABLE}: distances and ETAs will be straight lines " +
                        "over the ground, which is wrong for anything shown beside a price (B-23)",
                )
                StraightLineRouteEstimator()
            } else {
                GraphHopperRouteEstimator(config.osmFile, config.graphDirectory)
            }
    }
}
