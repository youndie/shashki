package io.github.youndie.shashki.ui.map.tiles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.ui.map.Projection
import io.github.youndie.shashki.ui.map.RouteLine

/**
 * One decoded tile, drawn on a Compose canvas. B-01's route-4 prototype.
 *
 * **What this is and is not.** It draws the layers the style documents draw, in their order, with
 * their colours — enough to answer "can Compose draw this map" with a picture. It does not read the
 * style JSON: the filters and the zoom interpolations are transcribed as Kotlin here, because the
 * question this prototype answers is about *drawing*, and a style interpreter is the part §1.8's
 * measurement already sized (thirteen layers, seven operators). Writing it before the drawing was
 * known to work would be building the cheap half first.
 *
 * The projection is the simplest one that is not a lie: the tile's own 0..extent grid scaled to the
 * viewport. A camera over many tiles is Web Mercator arithmetic and a tile cache, which §1.8b lists
 * and this does not pretend to have.
 *
 * **The tile covers the pane rather than fitting inside it** — `maxDimension`, not `minDimension`.
 * Fitting leaves a band of background wherever the pane is not square, which a real map never shows;
 * covering crops, which is what every map does at the edge of a tile. It matters because the golden
 * of a 390 × 440 map pane is design acceptance, and a band of nothing in it would be accepted.
 */
public class TileRenderer(
    private val palette: TilePalette = TilePalette.Dark,
) {
    /**
     * The areas: land, parks, water, buildings.
     *
     * **Split from the roads because a plane of tiles has to be drawn in layers, not in tiles.**
     * MVT geometry runs past the tile's own edge by a buffer, so neighbours overlap — and a renderer
     * that finished each tile before starting the next would paint one tile's water over its
     * neighbour's roads in that band. Every tile's areas first, then every tile's roads.
     *
     * [origin] is where this tile's top-left corner sits on the canvas and [side] is how big it is
     * drawn. Nothing is clipped: the overlap is the same geometry twice in the same place.
     */
    public fun DrawScope.drawTileAreas(
        tile: MvtTile,
        origin: Offset,
        side: Float,
    ) {
        tile.layer("landcover")?.let { draw(it, palette.landcover, origin, side, filled = true) }
        tile.layer("park")?.let { draw(it, palette.landcover, origin, side, filled = true) }
        tile.layer("water")?.let { draw(it, palette.water, origin, side, filled = true) }
        tile.layer("building")?.let { draw(it, palette.building, origin, side, filled = true) }
    }

    /** The roads, thinnest band first so a motorway is drawn over the street it crosses. */
    public fun DrawScope.drawTileRoads(
        tile: MvtTile,
        origin: Offset,
        side: Float,
    ) {
        val roads = tile.layer("transportation") ?: return
        for (band in ROAD_BANDS) {
            draw(
                layer = roads,
                colour = palette.road(band),
                origin = origin,
                side = side,
                filled = false,
                width = band.width,
                keep = { it.tags["class"] in band.classes },
            )
        }
    }

    /**
     * The route, in the two phases the style documents filter on.
     *
     * **Two strokes and not one line with a progress fraction**, because that is what the documents
     * describe: one GeoJSON source with a `phase` property and two `line` layers reading it. Drawing
     * it as one line and a marker would look the same on a still image and diverge the moment the
     * design asked for a different cap or a different width on one of them — `route-travelled` is
     * already `line-cap: butt` for a reason, and the reason is that the two ends meet.
     *
     * Travelled first, so the part still to drive is the one on top where they overlap at the car.
     */
    public fun DrawScope.drawRoute(
        route: RouteLine,
        projection: Projection,
    ) {
        strokePath(route.travelled, projection, palette.routeTravelled)
        strokePath(route.ahead, projection, palette.routeAhead)
    }

    private fun DrawScope.strokePath(
        points: List<GeoPoint>,
        projection: Projection,
        colour: Color,
    ) {
        if (points.size < 2) return
        val path = Path()
        points.forEachIndexed { index, point ->
            val at = projection.toCanvas(point)
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        drawPath(
            path,
            colour,
            style = Stroke(width = ROUTE_WIDTH, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        )
    }

    /**
     * The named roads, each written along itself. Separate from [drawTile] because it needs the
     * theme's typography, and a renderer that took a `TextStyle` to draw a polygon would be carrying
     * text into every call that has no text in it.
     */
    public fun streetLabels(
        tile: MvtTile,
        origin: Offset,
        side: Float,
    ): List<StreetLabel> {
        val layer = tile.layer("transportation_name") ?: return emptyList()
        val scale = side / layer.extent
        return layer.features.mapNotNull { feature ->
            val text = feature.labelText() ?: return@mapNotNull null
            val points = feature.paths.maxByOrNull { it.size } ?: return@mapNotNull null
            if (points.size < 4) return@mapNotNull null
            StreetLabel(text, points.readingLeftToRight().toPath(scale, origin), points.size)
        }
    }

    /**
     * The labels that fit, drawn; the rest dropped.
     *
     * **Collision and de-duplication are one pass and they have to be, because they are one problem.**
     * A street crossing a tile boundary is two features with one name, so a renderer that drew every
     * candidate wrote the name twice at the seam — and the same street, split into segments by every
     * junction, wrote it five more times down its own length. Research §1.8b listed label collision
     * as unbuilt; the seam is what made it unignorable.
     *
     * The rule: longest road first, one placement per name in the viewport, and a candidate whose box
     * touches an accepted one is dropped. Longest first is what makes the surviving placement the
     * most prominent stretch of that road rather than whichever segment the tile happened to list.
     *
     * What it deliberately does not do is push a label along its road to find a free spot, or repeat
     * a name down a long street the way a paper map does. Both are real and both are more than the
     * seam needs.
     */
    public fun DrawScope.drawStreetLabels(
        labels: List<StreetLabel>,
        measurer: TextMeasurer,
        style: TextStyle,
    ) {
        val taken = mutableListOf<Rect>()
        val named = mutableSetOf<String>()
        for (label in labels.sortedByDescending { it.weight }) {
            if (!named.add(label.text)) continue
            val bounds = labelBounds(measurer, label.text, label.path, style) ?: continue
            val padded = bounds.inflate(LABEL_PADDING)
            if (taken.any { it.overlaps(padded) }) continue
            taken += padded
            drawTextOnPath(measurer, label.text, label.path, style)
        }
    }

    private fun DrawScope.draw(
        layer: MvtLayer,
        colour: Color,
        origin: Offset,
        side: Float,
        filled: Boolean,
        width: Float = 1f,
        keep: (MvtFeature) -> Boolean = { true },
    ) {
        val scale = side / layer.extent
        for (feature in layer.features) {
            if (!keep(feature)) continue
            for (points in feature.paths) {
                if (points.size < 4) continue
                val path = points.toPath(scale, origin)
                if (filled) {
                    drawPath(
                        path,
                        colour,
                    )
                } else {
                    drawPath(path, colour, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }

    /**
     * The same road, wound so its name reads the right way up.
     *
     * **A road's geometry has a direction and it is not the reader's.** The glyphs are placed along
     * the tangent, so a street digitised east-to-west comes out mirrored — which the first goldens of
     * this renderer duly recorded as correct, because a screenshot test certifies whatever it is
     * shown. Half the street names on `screens_rider_trip_in_progress` were upside down.
     *
     * End points rather than the tangent at the midpoint: a road that bends back on itself has no
     * single direction, and the two ends are what decides which way the name should be read.
     */
    private fun IntArray.readingLeftToRight(): IntArray {
        if (size < 4 || this[0] <= this[size - 2]) return this
        val reversed = IntArray(size)
        var at = 0
        for (i in size - 2 downTo 0 step 2) {
            reversed[at++] = this[i]
            reversed[at++] = this[i + 1]
        }
        return reversed
    }

    private fun IntArray.toPath(
        scale: Float,
        origin: Offset,
    ): Path =
        Path().apply {
            moveTo(origin.x + this@toPath[0] * scale, origin.y + this@toPath[1] * scale)
            var i = 2
            while (i + 1 < this@toPath.size) {
                lineTo(origin.x + this@toPath[i] * scale, origin.y + this@toPath[i + 1] * scale)
                i += 2
            }
        }

    private companion object {
        /** Enough that two labels do not touch. Half a line of the size they are drawn at. */
        const val LABEL_PADDING = 6f

        /** The three road bands the styles paint, with the widths they set around zoom 14. */
        val ROAD_BANDS =
            listOf(
                RoadBand(setOf("minor", "residential", "service", "unclassified", "living_street"), width = 3f),
                RoadBand(setOf("primary", "secondary", "tertiary"), width = 6f),
                RoadBand(setOf("motorway", "trunk"), width = 10f),
            )

        /** `line-width: 6` in both documents, for both phases of the route. */
        const val ROUTE_WIDTH = 6f
    }
}

/** A street name and the stretch of road it would be written along. */
public data class StreetLabel(
    val text: String,
    val path: Path,
    /** How long the road segment is, in points. The longest stretch of a name is the one drawn. */
    val weight: Int,
)

internal data class RoadBand(
    val classes: Set<String>,
    val width: Float,
)

/** The colours the style documents open with, transcribed. Reading them from the JSON is later work. */
public data class TilePalette(
    val background: Color,
    val landcover: Color,
    val water: Color,
    val building: Color,
    val roadMinor: Color,
    val roadPrimary: Color,
    val roadMotorway: Color,
    /** `route-travelled`'s `line-color`: white at a quarter on the dark map, black on the light. */
    val routeTravelled: Color,
    /** `route-ahead`'s. The accent, and the same in both documents — the route is the one accent. */
    val routeAhead: Color,
) {
    internal fun road(band: RoadBand): Color =
        when (band.width) {
            3f -> roadMinor
            6f -> roadPrimary
            else -> roadMotorway
        }

    public companion object {
        public val Dark: TilePalette =
            TilePalette(
                background = Color(0xFF0A0A0A),
                landcover = Color(0xFF0D110D),
                water = Color(0xFF0E1A1F),
                building = Color(0xFF141414),
                roadMinor = Color(0xFF1C1C1C),
                roadPrimary = Color(0xFF262626),
                roadMotorway = Color(0xFF3A3A3A),
                routeTravelled = Color(0x40FFFFFF),
                routeAhead = Color(0xFF1BA1E2),
            )

        public val Light: TilePalette =
            TilePalette(
                background = Color(0xFFEFEFEF),
                landcover = Color(0xFFE4EBE2),
                water = Color(0xFFDCE4E8),
                building = Color(0xFFE2E2E2),
                roadMinor = Color(0xFFF7F7F7),
                roadPrimary = Color(0xFFFAFAFA),
                roadMotorway = Color(0xFFFFFFFF),
                routeTravelled = Color(0x40000000),
                routeAhead = Color(0xFF1BA1E2),
            )
    }
}

/** Where a point of the tile lands on the canvas, for anything drawn over the map. */
internal fun tileToCanvas(
    x: Int,
    y: Int,
    extent: Int,
    size: Size,
): Offset {
    val scale = size.maxDimension / extent
    return Offset(x * scale, y * scale)
}
