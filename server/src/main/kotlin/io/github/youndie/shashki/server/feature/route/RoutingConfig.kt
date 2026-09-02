package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

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

        /**
         * The config, or `null` when there is neither a prepared graph nor an extract to build one.
         *
         * **A prepared graph is enough on its own, and the first version of this said otherwise.**
         * `importOrLoad` reads the `.osm.pbf` only when the graph directory is empty, so a container
         * that carries 14 MB of prepared graph and no 41 MB extract routes perfectly — and this
         * function refused it, because it checked for the extract first. The image built in B-35 is
         * exactly that container, which is how it was found.
         */
        public fun fromEnv(env: (String) -> String? = System::getenv): RoutingConfig? {
            val osm = env(OSM_FILE_VARIABLE)?.let(Path::of)
            val graph =
                env(GRAPH_DIRECTORY_VARIABLE)?.let(Path::of)
                    ?: osm?.resolveSibling("graph-cache")
                    ?: return null
            return when {
                graph.isPrepared() -> RoutingConfig(osm ?: graph.resolve(NO_EXTRACT), graph)
                osm?.exists() == true -> RoutingConfig(osm, graph)
                else -> null
            }
        }

        /**
         * Whether the directory holds a graph rather than merely existing.
         *
         * GraphHopper writes several files and reads them back; an empty directory is what it treats
         * as "import needed", so that is the question asked here rather than `exists()`.
         */
        private fun Path.isPrepared(): Boolean = exists() && listDirectoryEntries().isNotEmpty()

        /**
         * The path GraphHopper is told to import from when there is nothing to import.
         *
         * It never reads it — the directory is prepared — but the builder requires a name, and one
         * that does not exist is better than one that might: an extract silently found beside the
         * graph would import over a graph somebody baked deliberately.
         */
        private const val NO_EXTRACT = "there-is-no-extract.osm.pbf"

        /**
         * The binding `rideModule` uses. Separate from [fromEnv] so the decision and the reason for
         * it are in one place rather than spread between a config object and a DI lambda.
         */
        public fun estimator(config: RoutingConfig? = fromEnv()): RouteEstimator =
            if (config == null) {
                LOG.warn(
                    "no prepared graph at ${'$'}{GRAPH_DIRECTORY_VARIABLE} and no extract at " +
                        "${'$'}{OSM_FILE_VARIABLE}: distances and ETAs will be straight lines over the " +
                        "ground, which is wrong for anything shown beside a price (B-23)",
                )
                StraightLineRouteEstimator()
            } else {
                GraphHopperRouteEstimator(config.osmFile, config.graphDirectory)
            }
    }
}
