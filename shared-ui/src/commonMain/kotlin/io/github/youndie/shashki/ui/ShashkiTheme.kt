package io.github.youndie.shashki.ui

import androidx.compose.runtime.Composable
import io.github.youndie.kvadrant.theme.KvadrantAccents
import io.github.youndie.kvadrant.theme.KvadrantColors
import io.github.youndie.kvadrant.theme.KvadrantTheme

/**
 * The two applications' themes, and the one place their accents are named.
 *
 * **This is a placeholder for [B-03](../../../../../../../docs/backlog/B-03-shashki-foundation-module.md)
 * and says so rather than pretending otherwise.** What it does not yet carry is the whole of what the
 * research found the kit needs: `ShashkiTypography` (four of the kit's seven type pairings are new
 * weights on stock sizes), `ShashkiMetrics` at 12 dp, and black ink on the accent — which
 * `KvadrantColors.onAccent` returns white for at both of these accents, by a rule that reproduces
 * Metro faithfully. The parameter that lets a caller say otherwise landed in kvadrant-ui B-48 and is
 * unreleased, so it is not reachable from the 0.1.0 this module compiles against.
 *
 * Research §1.1 has the numbers; B-03 is the item that puts them here.
 */
@Composable
public fun RiderTheme(content: @Composable () -> Unit) {
    KvadrantTheme(colors = KvadrantColors.dark(accent = KvadrantAccents.Cyan), content = content)
}

/**
 * Amber rather than red, and the kit's rule is the reason: red is reserved for cancellation in both
 * applications, so the driver's accent cannot be the colour its own decline button uses.
 */
@Composable
public fun DriverTheme(content: @Composable () -> Unit) {
    KvadrantTheme(colors = KvadrantColors.dark(accent = KvadrantAccents.Amber), content = content)
}
