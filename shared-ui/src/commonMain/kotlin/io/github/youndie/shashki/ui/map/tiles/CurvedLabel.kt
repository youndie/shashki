package io.github.youndie.shashki.ui.map.tiles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import kotlin.math.atan2

/**
 * A street name written along the street.
 *
 * **This is the piece research §1.8 called the hardest, and it turned out to have a second solution
 * that is better for this project.** §1.8 established that skia's `PathMeasure.getRSXform` and
 * `TextBlob.makeFromRSXform` are reachable from Kotlin/Wasm and would place glyphs on a curve — true,
 * and it needs an `org.jetbrains.skia.Typeface`, which means reaching past Compose for the font. The
 * font this product draws with is bundled *through* Compose by kvadrant, so the skia path would have
 * had to find it again by some other route, and a label drawn in a host font is a golden that
 * records the machine (B-02).
 *
 * Compose has its own `PathMeasure` with `getPosition` and `getTangent`, and its own `drawText`. One
 * glyph at a time, positioned and rotated, uses the theme's own typography — so the label is in
 * Selawik, the golden is portable, and nothing reaches around the framework. The skia route stays
 * true and stays the answer for anything Compose's text stack cannot express; this is not that.
 *
 * Per-glyph placement is a real cost and it is named: it measures each character separately, so
 * kerning between them is lost. At label sizes on a map that is invisible, and it is the same trade
 * every renderer that curves text makes.
 */
public fun DrawScope.drawTextOnPath(
    measurer: TextMeasurer,
    text: String,
    path: Path,
    style: TextStyle,
    startFraction: Float = 0.5f,
) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    if (length <= 0f) return

    val layouts = text.map { measurer.measure(AnnotatedString(it.toString()), style) }
    val textWidth = layouts.sumOf { it.size.width }.toFloat()
    if (textWidth > length) return

    // Centred on the requested fraction of the road, so a label sits in the middle of the segment
    // that is visible rather than at whichever end the geometry happens to start.
    var distance = (length * startFraction - textWidth / 2).coerceIn(0f, length - textWidth)

    for (layout in layouts) {
        val width = layout.size.width.toFloat()
        val centre = distance + width / 2
        val position = measure.getPosition(centre)
        val tangent = measure.getTangent(centre)
        val degrees = atan2(tangent.y, tangent.x) * DEGREES_PER_RADIAN
        rotate(degrees, position) {
            drawText(
                layout,
                topLeft = Offset(position.x - width / 2, position.y - layout.size.height / 2f),
            )
        }
        distance += width
    }
}

/**
 * The label a road carries, by the rule the styles *should* use.
 *
 * `ref` is the third branch, which the style documents do not have — 654 of the archive's 11 437
 * named roads carry nothing else, every one a motorway (B-24). A prototype that inherited the gap
 * would have drawn an unlabelled map and proved nothing about labels.
 */
public fun MvtFeature.labelText(): String? =
    tags["name:latin"]?.takeIf { it.isNotBlank() }
        ?: tags["name"]?.takeIf { it.isNotBlank() }
        ?: tags["ref"]?.takeIf { it.isNotBlank() }

private const val DEGREES_PER_RADIAN = 57.29578f
