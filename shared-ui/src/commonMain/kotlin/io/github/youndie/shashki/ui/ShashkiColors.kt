package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.youndie.kvadrant.theme.KvadrantAccents
import io.github.youndie.kvadrant.theme.KvadrantTheme

/**
 * The three colours that mean something rather than decorate, and none of them is a new number.
 *
 * The accent belongs to the theme — one per application, cyan for the rider and amber for the
 * driver — so it is not here. What is here is the semantics the kit assigns, and each value is a
 * stock accent named rather than a hex retyped: the same number written twice is the defect this
 * object exists to avoid.
 *
 * **Red is reserved for cancellation in both applications**, which is the kit's rule and the reason
 * the driver's accent is amber rather than red: an accent that collides with the one colour the
 * product uses to mean "this did not happen" cannot also mean "this is the thing to press".
 *
 * **Two of the three are constants and one is not.** [negative] and [positive] are accents, and an
 * accent is the same hex in both themes. [inactive] is a brush, and a brush is what changes between
 * `KvadrantColors.dark()` and `.light()` — so it is read from whatever theme is in composition
 * rather than pinned to the dark set. It used to be pinned, and that was wrong the moment the light
 * fixtures became scoped work (research §3, open question 1).
 */
public object ShashkiColors {
    /** Cancellation, decline, a lost fix. `KvadrantAccents.Red`. */
    public val negative: Color = KvadrantAccents.Red

    /** Paid, online, arrived. `KvadrantAccents.Green`. */
    public val positive: Color = KvadrantAccents.Green

    /** Unavailable and dead — a class with no cars, a driver who is offline. The current theme's `inactive`. */
    public val inactive: Color
        @Composable get() = KvadrantTheme.colors.inactive
}
