package io.github.youndie.shashki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantAccents
import io.github.youndie.kvadrant.theme.KvadrantTheme
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * The kit's seven type styles, at the kit's canvas, in the order the kit lists them.
 *
 * This is the acceptance for [ShashkiTypography]: four of the seven pair a stock size with a weight
 * the library pairs differently, so a golden of the ramp is the only thing that says the projection
 * came out right rather than merely compiling. The label beside each line names the size and weight
 * it claims to be, so a diff against the kit's specimen is readable without a ruler.
 */
@ViddikScreenshot(name = "type ramp", group = "foundation", width = 390, height = 844)
@Composable
internal fun ShashkiTypeRamp() {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        val type = ShashkiTheme.typography
        Column(
            Modifier
                .fillMaxSize()
                .background(KvadrantTheme.colors.background)
                .padding(KvadrantTheme.metrics.margin),
        ) {
            KvadrantText("54 / 200", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("$ 420", style = type.pageTitle)
            KvadrantText("32 / 200", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("$ 249", style = type.figure)
            KvadrantText("24 / 300", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("no cars nearby", style = type.stateHeadline)
            KvadrantText("19 / 300", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("trips", style = type.tileLabel)
            KvadrantText("17 / 400", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("Slovenska cesta 14", style = type.rowEmphasis)
            KvadrantText("15 / 400", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("comfort · 6 min · Skoda Octavia", style = type.body)
            KvadrantText("14 / 400", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
            KvadrantText("28 aug · 19:40 · comfort", style = type.meta.copy(color = KvadrantTheme.colors.subtle))
        }
    }
}

/**
 * The two applications' accents, side by side, on the surface the kit and the library disagree
 * about.
 *
 * The ink here is **white**, which is `contrastOn` reproducing Metro faithfully at 2.90:1 on cyan and
 * 2.11:1 on amber where the kit asks for black. This golden records the disagreement on purpose:
 * the parameter that resolves it landed in kvadrant-ui B-48 and is not in a published version yet,
 * so 0.1.0 cannot express the kit's answer. When it can, this image changes and that change is the
 * evidence.
 */
@ViddikScreenshot(name = "themes", group = "skeleton", width = 390, height = 844)
@Composable
internal fun SkeletonThemes() {
    Column(Modifier.fillMaxSize()) {
        AccentBand(Modifier.weight(1f).fillMaxWidth(), "rider · cyan", KvadrantAccents.Cyan)
        AccentBand(Modifier.weight(1f).fillMaxWidth(), "driver · amber", KvadrantAccents.Amber)
    }
}

@Composable
private fun AccentBand(
    modifier: Modifier,
    label: String,
    accent: Color,
) {
    val latin = kvadrantLatin()
    ShashkiTheme(accent = accent, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        Column(modifier.background(KvadrantTheme.colors.background).padding(KvadrantTheme.metrics.margin)) {
            KvadrantText(label, style = ShashkiTheme.typography.stateHeadline)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = KvadrantTheme.metrics.margin)
                    .background(KvadrantTheme.colors.accent)
                    .padding(KvadrantTheme.metrics.margin),
            ) {
                KvadrantText(
                    "$ 249",
                    style = ShashkiTheme.typography.figure.copy(color = KvadrantTheme.colors.onAccent),
                )
            }
        }
    }
}
