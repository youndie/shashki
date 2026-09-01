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
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantAccents
import io.github.youndie.kvadrant.theme.KvadrantColors
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.kvadrant.theme.KvadrantTypography
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * One fixture, at the kit's 390 × 844, whose whole job is that the acceptance path executes.
 *
 * A viddik plugin with no fixture generates no registry, records no golden and passes; the tasks
 * exist and say `NO-SOURCE`, which reads exactly like success. This makes "the screenshot suite is
 * wired" a checkable claim, and it is the file
 * [B-02](../../../../../../../docs/backlog/B-02-measure-golden-host-independence.md) measures with.
 *
 * It is deliberately not a screen — those arrive with `ShashkiTypography`, `ShashkiMetrics` and the
 * components in B-03 and B-04. What it does have to contain is an **accent-filled surface with ink
 * on it**, because that is the one thing the research found the library and the kit disagree about,
 * and a fixture that shows only text would diff identically whichever way that goes.
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
    KvadrantTheme(
        colors = KvadrantColors.dark(accent = accent),
        typography = KvadrantTypography.default(kvadrantLatin()).portable(),
    ) {
        Column(modifier.background(KvadrantTheme.colors.background).padding(12.dp)) {
            KvadrantText(label, style = KvadrantTheme.typography.large)
            // The accent as a surface, which is the whole reason this fixture exists. The kit asks
            // for black ink here; `KvadrantTheme.colors.onAccent` computes **white** at both of
            // these accents, faithfully reproducing Metro (research §1.1a). So this golden records
            // 2.90:1 on cyan and 2.11:1 on amber, on purpose — and B-03 is where it starts recording
            // what the kit asks for instead, through the parameter kvadrant-ui B-48 added.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(KvadrantTheme.colors.accent)
                    .padding(12.dp),
            ) {
                KvadrantText(
                    "$ 249",
                    style = KvadrantTheme.typography.extraLarge.copy(color = KvadrantTheme.colors.onAccent),
                )
            }
        }
    }
}
