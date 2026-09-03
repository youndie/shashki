package io.github.youndie.shashki.ui.kompot

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A style the boundary hands back with no colour in it is not neutral — it is Material's.**
 *
 * kompot's `resolveTextColor` takes the component's colour token, then the style's own colour, and
 * if neither is set falls back to `MaterialTheme.colorScheme.onSurface`. That is read out of
 * `kompot-client`'s `ComponentsKt` rather than assumed, and it is the right default for a toolkit
 * that must not presume a design system. Here it is `#1D1B20` on black — **1.23:1** — and the promo
 * screen's own title had been drawn that way since B-32 with a golden faithfully photographing it.
 *
 * So this asserts the property rather than the picture: every token this design system resolves
 * comes back with ink of its own, for every theme the product has.
 */
@OptIn(ExperimentalTestApi::class)
class DesignSystemInkTest {
    @Test
    fun `every typography token resolves with the kit's own ink`() {
        for (dark in listOf(true, false)) {
            val resolved = mutableMapOf<String, Color>()
            var foreground = Color.Unspecified
            runComposeUiTest {
                setContent {
                    Themed(dark) {
                        foreground = KvadrantTheme.colors.foreground
                        for (token in ShashkiTokens.TYPOGRAPHY) {
                            resolved[token] = ShashkiDesignSystem.resolveTypography(TypographyToken(token)).color
                        }
                        // The fallback a server one version ahead of this client lands on.
                        resolved["a token this build predates"] =
                            ShashkiDesignSystem.resolveTypography(TypographyToken("headline_2")).color
                    }
                }
            }

            assertEquals(
                ShashkiTokens.TYPOGRAPHY.size + 1,
                resolved.size,
                "the vocabulary is empty, so this test would pass over nothing",
            )
            val blank = resolved.filterValues { !it.isSpecified }.keys
            assertEquals(emptySet(), blank, "these fall back to Material's onSurface in kompot (dark = $dark)")
            assertTrue(
                resolved.values.all { it == foreground },
                "the ink is the kit's foreground, not something near it (dark = $dark)",
            )
        }
    }

    @Test
    fun `every colour token resolves to something the palette actually has`() {
        val resolved = mutableMapOf<String, Color>()
        runComposeUiTest {
            setContent {
                Themed(dark = true) {
                    val palette =
                        with(KvadrantTheme.colors) {
                            setOf(background, foreground, subtle, accent, onAccent, chrome)
                        }
                    for (token in ShashkiTokens.COLORS) {
                        val colour =
                            ShashkiDesignSystem.resolveColor(
                                io.github.youndie.kompot
                                    .ColorToken(token),
                            )
                        resolved[token] = colour
                        check(colour in palette) { "$token resolved outside the palette" }
                    }
                }
            }
        }

        assertEquals(ShashkiTokens.COLORS.size, resolved.size, "the vocabulary is empty; this would pass over nothing")
        // **Six names and five colours, and that is the kit rather than a bug.** `foreground` and
        // `on_accent` are both white on the dark theme — the ink on cyan is white here, which
        // `SkeletonFixtures` records as Metro reproduced faithfully at 2.90:1. An assertion that the
        // six were distinct was written first and was wrong about the design: what matters is that
        // each name lands *in* the palette, which is what the check above holds.
        assertEquals(5, resolved.values.toSet().size, "the palette this vocabulary covers")
    }

    @Composable
    private fun Themed(
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        val latin = kvadrantLatin()
        RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) { content() }
    }
}
