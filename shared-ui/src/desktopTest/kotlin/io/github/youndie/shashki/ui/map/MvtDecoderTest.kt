package io.github.youndie.shashki.ui.map

import io.github.youndie.shashki.ui.map.tiles.MvtGeometryType
import io.github.youndie.shashki.ui.map.tiles.decodeMvt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The decoder against a real tile out of the city's own archive: `z14/8850/5815`, 4 068 bytes, the
 * smallest tile in `city.pmtiles` that still carries both roads and a street name.
 *
 * **Every number asserted here was produced by a second reader, in Python, before this test
 * existed.** A test whose expectations come from running the code it tests agrees with the code
 * rather than checking it — and a geometry decoder is exactly the kind of thing that produces
 * confidently wrong output. The Python counts are in B-01's notes beside the tile.
 */
class MvtDecoderTest {
    private val tile by lazy {
        val bytes =
            checkNotNull(javaClass.classLoader.getResourceAsStream(TILE)) { "$TILE is not on the test classpath" }
                .use { it.readBytes() }
        decodeMvt(bytes)
    }

    @Test
    fun `the five layers the style names are all present, at the standard extent`() {
        assertEquals(
            listOf("building", "landcover", "place", "transportation", "transportation_name"),
            tile.layers.map { it.name },
        )
        assertTrue(tile.layers.all { it.extent == 4096 }, tile.layers.map { it.name to it.extent }.toString())
    }

    @Test
    fun `each layer holds the features an independent reader counted, of the geometry type it counted`() {
        assertEquals(2, tile.layer("building")!!.features.size)
        assertEquals(14, tile.layer("landcover")!!.features.size)
        assertEquals(3, tile.layer("place")!!.features.size)
        assertEquals(17, tile.layer("transportation")!!.features.size)
        assertEquals(1, tile.layer("transportation_name")!!.features.size)

        assertTrue(tile.layer("building")!!.features.all { it.type == MvtGeometryType.POLYGON })
        assertTrue(tile.layer("place")!!.features.all { it.type == MvtGeometryType.POINT })
        assertTrue(tile.layer("transportation")!!.features.all { it.type == MvtGeometryType.LINESTRING })
    }

    @Test
    fun `roads carry the class the style filters on, and their geometry is inside the tile`() {
        val roads = tile.layer("transportation")!!.features
        val classes = roads.mapNotNull { it.tags["class"] }.toSet()
        assertTrue(classes.isNotEmpty(), "no road carries a class tag: ${roads.map { it.tags }}")
        // The style's own filters: minor/residential/service/… , primary/secondary/tertiary,
        // motorway/trunk, rail. A class outside that set is a road nothing draws, which is fine —
        // what would not be fine is a tag the styles cannot match at all.
        assertTrue(
            classes.all { it.isNotBlank() },
            "blank class on a road: $classes",
        )

        val points = roads.flatMap { it.paths.toList() }
        assertTrue(points.isNotEmpty(), "roads decoded to no paths at all")
        assertTrue(points.all { it.size >= 4 }, "a line with fewer than two points is not a line")
        // Tile coordinates may run slightly outside 0..extent — that is the buffer a tile carries so
        // a road crossing the edge joins its neighbour without a seam. An order of magnitude out
        // means the zigzag decoding is wrong, which is the failure this catches.
        val worst = points.flatMap { it.toList() }.maxOf { kotlin.math.abs(it) }
        assertTrue(worst < 4096 * 4, "coordinate $worst is far outside the tile: zigzag or delta decoding is wrong")
    }

    /**
     * **This test found a defect in the styles, and asserts the data rather than the defect.**
     *
     * The one named road in this tile is the A2 motorway, and it carries `ref` and no `name` — which
     * is how OpenMapTiles labels a motorway. The style documents ask for
     * `["coalesce", ["get", "name:latin"], ["get", "name"]]`, so they would draw nothing on it.
     * Measured across the whole archive: 654 of 11 437 named roads are `ref`-only, every one a
     * motorway, and the road from the city to Brnik is one of them
     * ([B-24](../../../../../../../../docs/backlog/B-24-motorways-carry-ref-not-name.md)).
     *
     * So what is asserted is that the *tile* can be labelled — a polyline and some label-bearing
     * tag — because that is the requirement the renderer has of the data. Asserting the style's
     * current blindness instead would be a test that fails the day somebody fixes it.
     */
    @Test
    fun `the one named road is a polyline carrying a label, which is what a curved label needs`() {
        val named = tile.layer("transportation_name")!!.features.single()

        assertEquals(MvtGeometryType.LINESTRING, named.type)
        val label =
            named.tags.entries.firstOrNull { it.key == "name:latin" || it.key == "name" || it.key == "ref" }
        assertNotNull(label, "nothing to write on this road; it has ${named.tags}")
        assertTrue(label.value.isNotBlank())
        assertEquals("ref", label.key, "this tile's road is the A2, labelled by ref — see the KDoc above")

        val path = named.paths.first()
        assertTrue(path.size >= 6, "a label needs a path to follow: ${path.size / 2} points")
    }
}

private const val TILE = "tiles/ljubljana-14-8850-5815.mvt"
