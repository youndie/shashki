package io.github.youndie.shashki.ui.map

import androidx.compose.ui.geometry.Offset
import io.github.youndie.shashki.protocol.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** Which tile, in the scheme every tile server and every archive uses: zoom, column, row. */
public data class TileCoordinate(
    val zoom: Int,
    val x: Int,
    val y: Int,
)

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
) {
    private val tiles = 2.0.pow(tile.zoom)

    public fun toCanvas(point: GeoPoint): Offset {
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
    public fun toGeo(offset: Offset): GeoPoint {
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
