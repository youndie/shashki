package io.github.youndie.shashki.ui.map.tiles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

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
 */
public class TileRenderer(
    private val palette: TilePalette = TilePalette.Dark,
) {
    /** The basemap. Labels are [drawStreetLabels], because they need a `TextMeasurer` and a style. */
    public fun DrawScope.drawTile(tile: MvtTile) {
        drawRect(palette.background, size = size)

        tile.layer("landcover")?.let { draw(it, palette.landcover, filled = true) }
        tile.layer("park")?.let { draw(it, palette.landcover, filled = true) }
        tile.layer("water")?.let { draw(it, palette.water, filled = true) }
        tile.layer("building")?.let { draw(it, palette.building, filled = true) }

        // Roads by class, thinnest first, so a motorway is drawn over the residential street it
        // crosses — which is the order the style document lists them in and the reason it does.
        val roads = tile.layer("transportation") ?: return
        for (band in ROAD_BANDS) {
            draw(
                layer = roads,
                colour = palette.road(band),
                filled = false,
                width = band.width,
                keep = { it.tags["class"] in band.classes },
            )
        }
    }

    /**
     * The named roads, each written along itself. Separate from [drawTile] because it needs the
     * theme's typography, and a renderer that took a `TextStyle` to draw a polygon would be carrying
     * text into every call that has no text in it.
     */
    public fun DrawScope.drawStreetLabels(
        tile: MvtTile,
        measurer: TextMeasurer,
        style: TextStyle,
    ) {
        val layer = tile.layer("transportation_name") ?: return
        val scale = size.minDimension / layer.extent
        for (feature in layer.features) {
            val text = feature.labelText() ?: continue
            val points = feature.paths.maxByOrNull { it.size } ?: continue
            if (points.size < 4) continue
            drawTextOnPath(measurer, text, points.toPath(scale), style)
        }
    }

    private fun DrawScope.draw(
        layer: MvtLayer,
        colour: Color,
        filled: Boolean,
        width: Float = 1f,
        keep: (MvtFeature) -> Boolean = { true },
    ) {
        val scale = size.minDimension / layer.extent
        for (feature in layer.features) {
            if (!keep(feature)) continue
            for (points in feature.paths) {
                if (points.size < 4) continue
                val path = points.toPath(scale)
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

    private fun IntArray.toPath(scale: Float): Path =
        Path().apply {
            moveTo(this@toPath[0] * scale, this@toPath[1] * scale)
            var i = 2
            while (i + 1 < this@toPath.size) {
                lineTo(this@toPath[i] * scale, this@toPath[i + 1] * scale)
                i += 2
            }
        }

    private companion object {
        /** The three road bands the styles paint, with the widths they set around zoom 14. */
        val ROAD_BANDS =
            listOf(
                RoadBand(setOf("minor", "residential", "service", "unclassified", "living_street"), width = 3f),
                RoadBand(setOf("primary", "secondary", "tertiary"), width = 6f),
                RoadBand(setOf("motorway", "trunk"), width = 10f),
            )
    }
}

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
    val scale = size.minDimension / extent
    return Offset(x * scale, y * scale)
}
