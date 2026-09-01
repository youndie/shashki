package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

/**
 * The L-shaped fixture graph, imported once for the whole suite.
 *
 * A `GraphHopper` instance owns a directory and memory-maps it, so two of them over one directory is
 * a corrupt graph rather than an error — hence one object, and hence the temp directory that no test
 * chooses. The import takes tens of milliseconds on four nodes; the reason to share it is not speed
 * but that sharing is the only correct arrangement.
 */
internal object FixtureGraph {
    private val directory = createTempDirectory("shashki-fixture-graph")

    val estimator: GraphHopperRouteEstimator by lazy {
        val bytes =
            checkNotNull(javaClass.classLoader.getResourceAsStream(FIXTURE)) { "$FIXTURE is not on the test classpath" }
                .use { it.readBytes() }
        // GraphHopper chooses its reader by file extension, so the fixture has to reach the disk.
        val file = directory.resolve("l-shape.osm.xml")
        Files.write(file, bytes)
        GraphHopperRouteEstimator(
            osmFile = file,
            graphDirectory = directory.resolve("cache"),
            // GraphHopper's default of 200 drops connected components smaller than that, which is
            // right for a city and removes a four-node fixture entirely.
            minNetworkSize = 0,
        )
    }

    private const val FIXTURE = "osm/l-shape.osm.xml"
}
