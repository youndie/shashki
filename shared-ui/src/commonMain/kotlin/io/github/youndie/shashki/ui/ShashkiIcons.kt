package io.github.youndie.shashki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The kit's icons, transcribed from its SVG path data — six of the twenty-four the
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

    /**
     * A rating, one glyph per star (B-44).
     *
     * **A vector and not `★`.** The bundled face has neither U+2605 nor U+2606, and
     * `GlyphCoverageTest` said so the moment the first version of R8 used them: a character no
     * bundled font can draw falls back to whatever the host happens to have, which is a different
     * width and moves everything beside it. Filled and hollow are the same path with and without a
     * fill, so the two states cannot drift apart.
     */
    public fun star(filled: Boolean): ImageVector =
        vector("star-$filled") {
            val points = "M13 3.5l3 6.2 6.8 1-4.9 4.8 1.2 6.8-6.1-3.2-6.1 3.2 1.2-6.8L3.2 10.7l6.8-1z"
            if (filled) filled(points) else stroked(points)
        }

    /** Confirm: order, accept, arrived. The kit's tick. */
    public val check: ImageVector =
        vector("check") {
            stroked("M4 14l6 6L22 6")
        }

    /** The payment method on a row. */
    public val card: ImageVector =
        vector("card") {
            stroked("M2 6.5h22v13H2z")
            stroked("M2 10.5h22")
            stroked("M18 15.5h4")
        }

    /** Decline, cancel, close. */
    public val close: ImageVector =
        vector("close") {
            stroked("M6 6l14 14M20 6L6 20")
        }

    /**
     * A document's state, as a mark rather than a word (B-60).
     *
     * **The kit is explicit about this one**: *status is a glyph, not a badge — green tick, subtle
     * timer, accent camera for what is missing.* D1 shipped with the words `pending` and `missing`
     * right-aligned, which reads as a badge and spends a line of type on what a 20 dp mark says at a
     * glance.
     *
     * The third is a circle rather than the kit's camera, and that is a decision: this product
     * uploads a file through a picker, and a camera would promise one. An empty ring is what
     * "nothing here yet" looks like in a language with no illustrations.
     */
    public val tick: ImageVector =
        vector("tick") {
            stroked(circle(13f, 13f, 9f))
            stroked("M8.5 13.2l3 3 6-6.4")
        }

    /** In review: a clock, because what is being said is *not yet*. */
    public val timer: ImageVector =
        vector("timer") {
            stroked(circle(13f, 13f, 9f))
            stroked("M13 7.5V13l4 2.4")
        }

    /** Nothing sent. An empty ring: the shape of the other two with nothing in it. */
    public val empty: ImageVector =
        vector("empty") {
            stroked(circle(13f, 13f, 9f))
        }

    /** Back, for a window that has no other way out of a screen (B-67). A chevron, not an arrow. */
    public val back: ImageVector =
        vector("back") {
            stroked("M16 5L8 13l8 8")
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
