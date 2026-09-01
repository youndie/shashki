package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantAccents
import io.github.youndie.kvadrant.theme.KvadrantColors
import io.github.youndie.kvadrant.theme.KvadrantTheme

/**
 * The product's theme: kvadrant's, with the kit's ramp and the kit's spacing supplied to it.
 *
 * Colour is inherited and only checked — research §1.1 verified all seven dark brushes hex for hex —
 * so nothing is passed here but the accent. Type and spacing are not inherited and are supplied:
 * [ShashkiTypography] and [shashkiMetrics] say why in their own documentation.
 *
 * **Two ramps go in, and that is deliberate.** kvadrant's components read `KvadrantTheme.typography`,
 * so they get the kit's values projected onto the library's slots by [toKvadrant]; this product's own
 * components read [ShashkiTheme.typography], which has the kit's names. One source, two vocabularies,
 * and the projection is the only place they meet.
 */
@Composable
public fun ShashkiTheme(
    accent: Color,
    dark: Boolean = true,
    latin: FontFamily = kvadrantLatin(),
    // A parameter for one reason, and it is the same reason `KvadrantTheme` has one: a golden has to
    // be able to pin hinting and smoothing on every slot, and the pin lives in the test source set
    // where this default cannot reach it.
    typography: ShashkiTypography = remember(latin) { ShashkiTypography.of(latin) },
    content: @Composable () -> Unit,
) {
    // **Black ink on the accent, in both themes, through the library's parameter.** The kit asks for
    // black on a filled accent surface; `contrastOn` computes white at both of this product's accents,
    // faithfully reproducing Metro (2.90:1 on cyan, 2.11:1 on amber — research §1.1a). kvadrant-ui
    // 0.2.0 made the ink a constructor parameter with the computed value as its default, which is
    // exactly the lever a fixed-hex accent needs: the accent stays where the kit put it and only the
    // ink moves. Ink on an accent is a property of the accent, not of the theme around it, so the
    // light theme passes the same value.
    val colors =
        if (dark) {
            KvadrantColors.dark(accent = accent, onAccent = Color.Black)
        } else {
            KvadrantColors.light(accent = accent, onAccent = Color.Black)
        }

    CompositionLocalProvider(LocalShashkiTypography provides typography) {
        KvadrantTheme(
            colors = colors,
            typography = typography.toKvadrant(latin),
            metrics = shashkiMetrics(),
            content = content,
        )
    }
}

/** Cyan. The rider's application. */
@Composable
public fun RiderTheme(
    dark: Boolean = true,
    latin: FontFamily = kvadrantLatin(),
    typography: ShashkiTypography = remember(latin) { ShashkiTypography.of(latin) },
    content: @Composable () -> Unit,
): Unit = ShashkiTheme(KvadrantAccents.Cyan, dark, latin, typography, content)

/**
 * Amber, and the kit's rule is the reason it is not red: red is reserved for cancellation in both
 * applications, so the driver's accent cannot be the colour its own decline button uses.
 */
@Composable
public fun DriverTheme(
    dark: Boolean = true,
    latin: FontFamily = kvadrantLatin(),
    typography: ShashkiTypography = remember(latin) { ShashkiTypography.of(latin) },
    content: @Composable () -> Unit,
): Unit = ShashkiTheme(KvadrantAccents.Amber, dark, latin, typography, content)

/** What is in composition. `ShashkiTheme.typography` beside `KvadrantTheme.colors`. */
public object ShashkiTheme {
    public val typography: ShashkiTypography
        @Composable get() = LocalShashkiTypography.current
}

/**
 * Provided by [ShashkiTheme], and only by it.
 *
 * **No default, on purpose.** A default of `ShashkiTypography.of(FontFamily.Default)` would let a
 * call site outside the theme render — in the platform's fallback face, at the right sizes, looking
 * almost right — and a golden of it would pass. That is the failure that is hardest to find, so the
 * stray call site fails on first read instead, with the message naming what it forgot.
 */
public val LocalShashkiTypography: ProvidableCompositionLocal<ShashkiTypography> =
    staticCompositionLocalOf {
        error("ShashkiTheme.typography read outside ShashkiTheme: wrap the call site in RiderTheme or DriverTheme")
    }
