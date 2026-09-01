package io.github.youndie.shashki.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.youndie.kvadrant.theme.KvadrantFontSizes
import io.github.youndie.kvadrant.theme.KvadrantTypography
import io.github.youndie.kvadrant.theme.KvadrantWeights

/**
 * The kit's type ramp, by the kit's own names.
 *
 * **Every size here is kvadrant's and four of the seven weights are not**, which is why this exists
 * instead of a call to `KvadrantTypography.default`. Research §1.1 has the table; the short version
 * is that the kit pairs stock sizes with weights the library pairs differently, and the disagreement
 * is not rounding — [rowEmphasis] is 17 sp at W400 where the library's slot of that size is SemiBold.
 *
 * **One of the names is a trap.** `KvadrantTypography.pageTitle` is 14 sp at W400 — Metro's
 * `ApplicationTitle`, the small line above a page header — while the kit's [pageTitle] is 54 sp at
 * W200. A mapping written from the names alone lands the wrong style on every page header in the
 * product, so the two vocabularies are kept apart and [toKvadrant] is the only place they meet.
 *
 * The kit's invariant — "nothing between 32 and 54, a fare and a page title are different objects" —
 * is why there is no slot between [figure] and [pageTitle]. Adding one is a change to the design.
 */
@Immutable
public data class ShashkiTypography(
    /** 54 / W200. Page titles, and the primary figure: an offer's fare, a countdown, a day's takings. */
    val pageTitle: TextStyle,
    /** 32 / W200. Prices and every secondary figure. */
    val figure: TextStyle,
    /** 24 / W300. The headline of a state screen — matching, no cars nearby, payment failed. */
    val stateHeadline: TextStyle,
    /** 19 / W300. Pivot items and tile labels. */
    val tileLabel: TextStyle,
    /** 17 / W400. The emphasised half of a list row. */
    val rowEmphasis: TextStyle,
    /** 15 / W400. List titles, body copy, buttons. */
    val body: TextStyle,
    /** 14 / W400, drawn in the subtle brush by its call sites. Row subtitles and meta. */
    val meta: TextStyle,
) {
    public companion object {
        /**
         * [family] is a parameter for the same reason it is one in the library: a theme that loads
         * its own font files only works on the platform whose loader it hard-codes.
         *
         * **The two figures are tabular and the rest are not.** A fare that changes while a counter
         * runs, and a countdown that changes every second, both shift their neighbours if the digits
         * are proportional; a street name does not have that problem and does not need the
         * narrower digits.
         */
        public fun of(family: FontFamily): ShashkiTypography =
            ShashkiTypography(
                pageTitle =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.ExtraExtraLarge,
                        fontWeight = KvadrantWeights.Light,
                        fontFeatureSettings = TABULAR,
                    ),
                figure =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.ExtraLarge,
                        fontWeight = KvadrantWeights.Light,
                        fontFeatureSettings = TABULAR,
                    ),
                stateHeadline =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.Large,
                        fontWeight = KvadrantWeights.SemiLight,
                    ),
                tileLabel =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.MediumLarge,
                        fontWeight = KvadrantWeights.SemiLight,
                    ),
                rowEmphasis =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.Medium,
                        fontWeight = KvadrantWeights.Normal,
                    ),
                body =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.Normal,
                        fontWeight = KvadrantWeights.Normal,
                    ),
                meta =
                    TextStyle(
                        fontFamily = family,
                        fontSize = KvadrantFontSizes.Small,
                        fontWeight = KvadrantWeights.Normal,
                    ),
            )

        /** OpenType tabular figures, so a digit that changes does not move the digits beside it. */
        private const val TABULAR: String = "tnum"
    }
}

/**
 * The same ramp projected onto the library's slots, because kvadrant's own components read
 * `KvadrantTheme.typography` and would otherwise draw in a ramp nobody chose.
 *
 * **The library's `pageTitle` slot takes the kit's [meta]**, because that slot is the small
 * application title; the kit's own [pageTitle] goes to `pivotHeader`, which is the 54 sp one. The two
 * panorama styles are left as the library built them — this product draws no panorama, and giving
 * values to a surface it does not have would be inventing numbers.
 */
public fun ShashkiTypography.toKvadrant(family: FontFamily): KvadrantTypography =
    KvadrantTypography.default(family).copy(
        normal = body,
        subtle = meta,
        title = rowEmphasis,
        mediumLarge = tileLabel,
        large = stateHeadline,
        extraLarge = figure,
        pageTitle = meta,
        pivotHeader = pageTitle,
    )
