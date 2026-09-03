package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * B-23's timing criterion, on the city's own extract.
 *
 * **Skipped unless the extract is there, and that is stated rather than hidden.** The archive is
 * 41 MB and is not in git (B-06), so neither CI nor a fresh checkout runs this; it is run by hand
 * against the city extract `map/city_tiles.sh` produces, and the number is written
 * into B-23. A test that quietly skips is a test that proves nothing, so the reason it skipped is in
 * the message and the measured figures are in the backlog item rather than only here.
 *
 * ```bash
 * SHASHKI_OSM_FILE=<city>/Ljubljana.osm.pbf ./gradlew :server:test --tests '*CityGraphMeasurement*'
 * ```
 */
class CityGraphMeasurement {
    @Test
    fun `a route across the city answers in under 50 ms on a cached graph`() {
        val extract = System.getenv(RoutingConfig.OSM_FILE_VARIABLE)?.let(Path::of)
        assumeTrue(
            extract != null && extract.exists(),
            "no ${RoutingConfig.OSM_FILE_VARIABLE}: this measurement needs the city extract, which is not in git (B-06)",
        )

        val cache = requireNotNull(extract).resolveSibling("graph-cache-b23")
        val firstStart = measureTime { GraphHopperRouteEstimator(extract, cache).close() }
        val estimator = GraphHopperRouteEstimator(extract, cache)
        val cachedStart =
            measureTime { GraphHopperRouteEstimator(extract, cache.resolveSibling("graph-cache-b23")).close() }

        try {
            // A cold JIT measures the JIT. The first routes are the warm-up and are thrown away.
            repeat(WARMUP) { estimator.estimate(CENTRE, AIRPORT) }

            val samples = List(RUNS) { measureTime { estimator.estimate(CENTRE, AIRPORT) }.inWholeMicroseconds }
            val sorted = samples.sorted()
            val median = sorted[RUNS / 2]
            val worst = sorted.last()
            val route = estimator.estimate(CENTRE, AIRPORT)

            println(
                "B-23 measurement: import ${firstStart.inWholeMilliseconds} ms, cached open " +
                    "${cachedStart.inWholeMilliseconds} ms, route median $median us, worst $worst us, " +
                    "${route.distanceMetres} m / ${route.durationSeconds} s, ${route.geometry.size} points",
            )

            // The criterion is about the answer a client waits for, so the worst of the run is the
            // one that has to hold — a median under the bar with a tail over it is a demo that
            // stutters exactly when somebody is watching.
            assertTrue(worst < LIMIT_MICROS, "worst of $RUNS routes was $worst us, over the ${LIMIT_MICROS} us bar")
        } finally {
            estimator.close()
        }
    }

    private companion object {
        val CENTRE = GeoPoint(46.0511, 14.5051)
        val AIRPORT = GeoPoint(46.2237, 14.4576)
        const val WARMUP = 50
        const val RUNS = 201
        const val LIMIT_MICROS = 50_000L
    }
}
