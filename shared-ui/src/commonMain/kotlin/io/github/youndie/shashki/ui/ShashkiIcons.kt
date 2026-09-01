package io.github.youndie.shashki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The kit's icons, transcribed from its SVG path data — the first four of the twenty-four the
 * handoff (§1.4) says are new. Drawn on the same 26-unit grid as the library's forty, one stroke
 * weight, butt caps, no fill except where a dot or a wheel carries meaning, no corner rounding.
 *
 * Every path here is the kit's `d` attribute verbatim. Colour is not baked in: each is white and is
 * tinted at the call site, so one vector serves black-on-accent, white-on-chrome and the disabled
 * brush without three copies.
 */
public object ShashkiIcons {
    /** Economy: one rect under the body. Comfort: two. Business: three. The kit draws the class as a count. */
    public fun car(rects: Int): ImageVector =
        vector("car-$rects") {
            stroked("M2.5 15.4v-3.4l3-1 2.3-4.5h9.4l2.3 4.5 3 1v3.4z")
            stroked("M8 11h10")
            filled(circle(7.8f, 15.4f, 2.2f))
            filled(circle(18.2f, 15.4f, 2.2f))
            val xs =
                when (rects) {
                    1 -> listOf(11.5f)
                    2 -> listOf(8f, 15f)
                    else -> listOf(5f, 11.5f, 18f)
                }
            for (x in xs) filled("M$x 20.5h3v3h-3z")
        }

    /** A circle on a stem: where the driver goes first. */
    public val pinPickup: ImageVector =
        vector("pin-pickup") {
            stroked(circle(13f, 9f, 5f))
            stroked("M13 14v10")
        }

    /** A square on a stem: where the trip ends. */
    public val pinDropoff: ImageVector =
        vector("pin-dropoff") {
            stroked("M8.5 4.5h9v9h-9z")
            stroked("M13 13.5V24")
        }

    /** Decline, cancel, close. */
    public val close: ImageVector =
        vector("close") {
            stroked("M6 6l14 14M20 6L6 20")
        }

    private fun vector(
        name: String,
        build: ImageVector.Builder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 26.dp,
                defaultHeight = 26.dp,
                viewportWidth = 26f,
                viewportHeight = 26f,
            ).apply(build)
            .build()

    private fun ImageVector.Builder.stroked(d: String) {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }

    private fun ImageVector.Builder.filled(d: String) {
        addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White))
    }

    /** SVG has `<circle>`; path data does not, so a circle is two half-turn arcs. */
    private fun circle(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"
}
