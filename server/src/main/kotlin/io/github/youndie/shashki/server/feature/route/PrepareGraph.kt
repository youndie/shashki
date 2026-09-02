package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.server.feature.route.data.GraphHopperRouteEstimator
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Prepare the road graph an image will carry, using **this server's own configuration**.
 *
 * **A graph is only valid for the profile that built it, and that is not a detail.** GraphHopper
 * stores a hash of the profile and the encoded values beside the data and refuses a mismatch —
 * `Profiles do not match: car|-1705186244 vs car|26199302`. The first image baked a graph that
 * happened to be on the build machine, from a measurement run months of commits ago, and died on
 * start with exactly that message (B-35). A graph prepared by anything other than the code that will
 * read it is a landmine with a date on it.
 *
 * So the image takes **one** input — the extract, which B-06 says how to obtain — and this makes the
 * graph from it with the same `GraphHopperRouteEstimator` the server runs. The import costs about
 * three seconds; an option to supply a ready-made graph would save them and buy the mismatch back.
 *
 *     java -cp … io.github.youndie.shashki.server.feature.route.PrepareGraphKt <extract> <into>
 */
public fun main(args: Array<String>) {
    require(args.size == 2) { "usage: prepare-graph <osm extract> <graph directory>" }
    val extract = Path.of(args[0])
    val into = Path.of(args[1])
    require(extract.exists()) { "no extract at $extract — map/city_tiles.sh builds one" }

    GraphHopperRouteEstimator(extract, into).use {
        // Opening it is the preparation; closing it is what flushes the directory to disk. There is
        // nothing to ask it — a route here would be a test, and the tests are elsewhere.
    }
    println("prepared $into from $extract")
}
