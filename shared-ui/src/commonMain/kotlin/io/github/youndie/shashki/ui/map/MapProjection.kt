package io.github.youndie.shashki.ui.map

import androidx.compose.ui.geometry.Offset
import io.github.youndie.shashki.protocol.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** Which tile, in the scheme every tile server and every archive uses: zoom, column, row. */
public data class TileCoordinate(
    val zoom: Int,
    val x: Int,
    val y: Int,
) {
    public companion object {
        /**
         * The tile a point falls in, at [zoom]. **A camera of one tile**, which is what route 4 has
         * until tile fetching exists — enough to place a route, a car and two pins where they
         * actually are, and not enough to pan.
         */
        public fun containing(
            point: GeoPoint,
            zoom: Int = DEFAULT_ZOOM,
        ): TileCoordinate {
            val tiles = 2.0.pow(zoom)
            val latitude = point.lat * PI / 180.0
            val worldX = (point.lon + 180.0) / 360.0 * tiles
            val worldY = (1.0 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2.0 * tiles
            return TileCoordinate(zoom, worldX.toInt(), worldY.toInt())
        }

        /** The zoom the style documents are cut and drawn at. */
        public const val DEFAULT_ZOOM: Int = 14
    }
}

/**
 * Earth to canvas, whichever way the map is framed.
 *
 * **Two implementations and not one, because they are two different pictures.** [TileProjection] is
 * one tile filling a pane, which is what route 4's prototype had and what the single-tile goldens
 * still are; [MapViewport] is a camera over a plane of tiles, which is what an application needs the
 * moment it can pan. A screen names neither — it hands over a [MapScene] and the surface decides.
 */
public interface Projection {
    public fun toCanvas(point: GeoPoint): Offset

    public fun toGeo(offset: Offset): GeoPoint
}

/**
 * A camera: a centre, a zoom, and a pane to fill.
 *
 * **The zoom is fractional and the tiles are not.** Tiles exist at integer zooms only, so the whole
 * plane is drawn at `floor(zoom)` and scaled by the remainder — which is how every slippy map works
 * and why a half-zoom does not fetch a different set of tiles.
 *
 * [tiles] is the set the pane actually touches, clamped to the world: at the edge of the plane there
 * is no column −1, and asking for one would be a request that can only 404.
 */
public class MapViewport(
    public val camera: MapCamera,
    private val width: Float,
    private val height: Float,
    private val tileSide: Float,
) : Projection {
    /** The integer zoom the tiles come from. */
    public val zoom: Int = camera.zoom.toInt()

    private val world = 1 shl zoom
    private val scale = tileSide * 2.0.pow(camera.zoom - zoom).toFloat()
    private val centre = worldOf(camera.centre)

    override fun toCanvas(point: GeoPoint): Offset {
        val at = worldOf(point)
        return Offset(
            ((at.first - centre.first) * scale + width / 2).toFloat(),
            ((at.second - centre.second) * scale + height / 2).toFloat(),
        )
    }

    override fun toGeo(offset: Offset): GeoPoint {
        val worldX = (offset.x - width / 2) / scale + centre.first
        val worldY = (offset.y - height / 2) / scale + centre.second
        val lon = worldX / world * DEGREES_PER_TURN - DEGREES_PER_HALF_TURN
        val lat = atan(sinh(PI * (1 - 2 * worldY / world))) * DEGREES_PER_HALF_TURN / PI
        return GeoPoint(lat, lon)
    }

    /** Where this tile's own top-left corner lands on the canvas. */
    public fun originOf(tile: TileCoordinate): Offset =
        Offset(
            ((tile.x - centre.first) * scale + width / 2).toFloat(),
            ((tile.y - centre.second) * scale + height / 2).toFloat(),
        )

    /** How big a tile is drawn, in pixels. */
    public val drawnTileSide: Float get() = scale

    /**
     * Every tile the pane touches, reading order.
     *
     * **Reading order matters when they are drawn**: a tiling renderer that painted them in an
     * arbitrary order would put one tile's water over its neighbour's roads in the overlap the
     * format leaves at every edge.
     */
    public fun tiles(): List<TileCoordinate> {
        val halfX = width / 2 / scale
        val halfY = height / 2 / scale
        val fromX = floor(centre.first - halfX).toInt()
        val toX = floor(centre.first + halfX).toInt()
        val fromY = floor(centre.second - halfY).toInt()
        val toY = floor(centre.second + halfY).toInt()
        val result = mutableListOf<TileCoordinate>()
        for (y in fromY..toY) {
            for (x in fromX..toX) {
                if (x in 0 until world && y in 0 until world) result += TileCoordinate(zoom, x, y)
            }
        }
        return result
    }

    private fun worldOf(point: GeoPoint): Pair<Double, Double> {
        val latitude = point.lat * PI / DEGREES_PER_HALF_TURN
        val worldX = (point.lon + DEGREES_PER_HALF_TURN) / DEGREES_PER_TURN * world
        val worldY = (1.0 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2.0 * world
        return worldX to worldY
    }

    private companion object {
        const val DEGREES_PER_TURN = 360.0
        const val DEGREES_PER_HALF_TURN = 180.0
    }
}

/**
 * Web Mercator, between a place on the earth and a pixel on one tile drawn at [side] pixels square.
 *
 * **[side] and not the canvas's width and height**, because that is what `TileRenderer` uses:
 * `size.maxDimension / extent`, the tile covering the pane rather than fitting inside it. A
 * projection that spread the tile over a non-square pane would put the route beside the road it
 * belongs to, and the picture would look almost right — which is the worst kind of wrong for
 * something whose only test is a screenshot.
 */
public class TileProjection(
    private val tile: TileCoordinate,
    private val side: Float,
) : Projection {
    private val tiles = 2.0.pow(tile.zoom)

    override fun toCanvas(point: GeoPoint): Offset {
        val latitude = point.lat * PI / DEGREES_PER_HALF_TURN
        val worldX = (point.lon + DEGREES_PER_HALF_TURN) / DEGREES_PER_TURN * tiles
        val worldY = (1.0 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2.0 * tiles
        return Offset(((worldX - tile.x) * side).toFloat(), ((worldY - tile.y) * side).toFloat())
    }

    /**
     * The inverse. It exists because the fixtures need it: a route drawn beside the road it follows
     * is a golden of the wrong picture, so the demonstration route is lifted out of the tile's own
     * road geometry and turned back into coordinates rather than typed in by hand.
     */
    override fun toGeo(offset: Offset): GeoPoint {
        val worldX = offset.x / side + tile.x
        val worldY = offset.y / side + tile.y
        val lon = worldX / tiles * DEGREES_PER_TURN - DEGREES_PER_HALF_TURN
        val lat = atan(sinh(PI * (1 - 2 * worldY / tiles))) * DEGREES_PER_HALF_TURN / PI
        return GeoPoint(lat, lon)
    }

    private companion object {
        const val DEGREES_PER_TURN = 360.0
        const val DEGREES_PER_HALF_TURN = 180.0
    }
}
