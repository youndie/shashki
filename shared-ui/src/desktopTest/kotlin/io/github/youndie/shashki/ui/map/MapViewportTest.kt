package io.github.youndie.shashki.ui.map

import io.github.youndie.shashki.protocol.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The camera, which is the piece that decides what gets fetched.
 *
 * **A viewport that asked for the wrong tiles would still draw a map.** It would draw the
 * neighbouring square kilometre, with the route and the pins in exactly the right place on it —
 * which is why this is arithmetic with assertions rather than something a golden could catch.
 */
class MapViewportTest {
    /** The corner where four tiles meet, at zoom 14. The same point the seam golden is centred on. */
    private val seam = GeoPoint(46.04273565, 14.52392578)

    /** Well inside one tile: the centre of `14/8852/5825`. */
    private val inside = GeoPoint(46.04653, 14.51294)

    @Test
    fun `a pane on a corner touches four tiles and a pane inside one touches one`() {
        val onTheSeam = MapViewport(MapCamera(seam, zoom = 14.0), width = 390f, height = 390f, tileSide = 512f)
        assertEquals(4, onTheSeam.tiles().size)
        assertEquals(
            setOf(
                TileCoordinate(14, 8852, 5825),
                TileCoordinate(14, 8853, 5825),
                TileCoordinate(14, 8852, 5826),
                TileCoordinate(14, 8853, 5826),
            ),
            onTheSeam.tiles().toSet(),
        )

        // 100 px of a 512 px tile, centred well inside it: one tile and no speculative neighbours.
        val within = MapViewport(MapCamera(inside, zoom = 14.0), width = 100f, height = 100f, tileSide = 512f)
        assertEquals(listOf(TileCoordinate(14, 8852, 5825)), within.tiles())
    }

    /**
     * **The tiles are drawn in reading order and that is not decoration.** MVT geometry runs past
     * the tile's own edge, so neighbours overlap; a renderer painting them in an arbitrary order
     * puts one tile's water over the next one's roads in that band.
     */
    @Test
    fun `the tiles come back in reading order`() {
        val viewport = MapViewport(MapCamera(seam, zoom = 14.0), width = 390f, height = 390f, tileSide = 512f)

        assertEquals(
            listOf(
                TileCoordinate(14, 8852, 5825),
                TileCoordinate(14, 8853, 5825),
                TileCoordinate(14, 8852, 5826),
                TileCoordinate(14, 8853, 5826),
            ),
            viewport.tiles(),
        )
    }

    /** The camera's own centre lands in the middle of the pane. Anything else is a map that is off. */
    @Test
    fun `the centre of the camera is the centre of the pane`() {
        val viewport = MapViewport(MapCamera(inside, zoom = 14.0), width = 390f, height = 844f, tileSide = 512f)

        val at = viewport.toCanvas(inside)

        assertTrue(abs(at.x - 195f) < 0.5f, "x was ${at.x}")
        assertTrue(abs(at.y - 422f) < 0.5f, "y was ${at.y}")
    }

    /**
     * A tile's corner and the projection of that corner's coordinates are the same pixel.
     *
     * **Two code paths that must agree**: `originOf` places the tile's bitmap and `toCanvas` places
     * the route and the pins on top of it. A disagreement of a few pixels is a route running beside
     * the road it belongs to, which looks almost right — the worst kind of wrong for a screenshot.
     */
    @Test
    fun `where a tile is drawn and where its own corner projects are the same point`() {
        val viewport = MapViewport(MapCamera(seam, zoom = 14.0), width = 390f, height = 390f, tileSide = 512f)
        val tile = TileCoordinate(14, 8853, 5826)

        val origin = viewport.originOf(tile)
        val corner = viewport.toCanvas(viewport.toGeo(origin))

        assertTrue((origin - corner).getDistance() < 0.5f, "$origin against $corner")
    }

    /** Half a zoom in is twice the pixels per tile, and the same tiles: they only exist at integers. */
    @Test
    fun `a fractional zoom scales the same tiles instead of fetching different ones`() {
        val whole = MapViewport(MapCamera(inside, zoom = 14.0), width = 390f, height = 390f, tileSide = 512f)
        val fraction = MapViewport(MapCamera(inside, zoom = 14.5), width = 390f, height = 390f, tileSide = 512f)

        assertEquals(14, fraction.zoom)
        assertTrue(fraction.drawnTileSide > whole.drawnTileSide, "a closer camera draws a bigger tile")
        assertTrue(fraction.tiles().all { it.zoom == 14 })
    }

    /** The plane does not wrap and the world has no column −1: asking for one can only 404. */
    @Test
    fun `the edge of the world is clamped rather than requested`() {
        val corner = MapViewport(MapCamera(GeoPoint(85.0, -180.0), zoom = 2.0), 390f, 390f, tileSide = 512f)

        assertTrue(corner.tiles().all { it.x >= 0 && it.y >= 0 }, "${corner.tiles()}")
        assertTrue(corner.tiles().all { it.x < 4 && it.y < 4 })
    }
}
