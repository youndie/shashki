package io.github.youndie.shashki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.components.KvadrantButton
import io.github.youndie.kvadrant.components.KvadrantListItem
import io.github.youndie.kvadrant.components.KvadrantPageHeader
import io.github.youndie.kvadrant.components.KvadrantPivotHeaders
import io.github.youndie.kvadrant.components.KvadrantTextBox
import io.github.youndie.kvadrant.components.KvadrantTile
import io.github.youndie.kvadrant.components.TileSize
import io.github.youndie.kvadrant.components.rememberKvadrantPivotState
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.protocol.TripRow
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * Every stock component this product will actually draw, under [RiderTheme], laid out as the kit's
 * rows — so the golden answers B-21's question by picture rather than by reading the library.
 *
 * The projection in [toKvadrant] maps the kit's styles onto the library's slots by size. A slot is
 * not a size: it is what a library *component* reads, and the golden is where a component whose
 * slot the kit uses at a different size shows up. Each block is captioned with the slot the
 * component reads and the size the kit draws that element at, so a diff needs no ruler: where the
 * two captions disagree, the picture is the finding.
 *
 * Strings are the ones the ramp fixture already proved through the bundled face
 * ([B-05](../../../../../../../docs/backlog/B-05-glyph-coverage-guard.md) guards the rest once it
 * exists).
 */
@ViddikScreenshot(name = "stock components", group = "foundation", width = 390, height = 844)
@Composable
internal fun StockComponentsDark() = StockComponents(dark = true)

@ViddikScreenshot(name = "stock components light", group = "foundation", width = 390, height = 844)
@Composable
internal fun StockComponentsLight() = StockComponents(dark = false)

@Composable
private fun StockComponents(dark: Boolean) {
    val latin = kvadrantLatin()
    RiderTheme(dark = dark, latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        val colors = KvadrantTheme.colors
        val metrics = KvadrantTheme.metrics
        val meta = ShashkiTheme.typography.meta.copy(color = colors.subtle)
        Column(Modifier.fillMaxSize().background(colors.background)) {
            // PageHeader reads pageTitle (= kit meta 14) and pivotHeader (= kit pageTitle 54).
            KvadrantPageHeader(applicationTitle = "SHASHKI", pageTitle = "trips")

            Column(Modifier.padding(horizontal = metrics.margin)) {
                KvadrantText(
                    "pivot headers — read `pivotHeader` (54/200 here) · kit: pivot header 19/300",
                    style = meta,
                )
                KvadrantPivotHeaders(
                    titles = listOf("trips", "profile", "promo"),
                    state = rememberKvadrantPivotState(3),
                )
                Spacer(Modifier.height(metrics.margin))

                KvadrantText("list rows — read `normal` (15/400) · kit: list title 15/400 · R9", style = meta)
            }
            // The kit's TripRow, twice: a completed trip and a cancelled one. ListItem reads
            // `normal` for the title and `subtle` for the subtitle — the projection's body and meta.
            KvadrantListItem(
                title = "Slovenska cesta 15 · Airport, terminal B",
                subtitle = "28 aug · 19:40 · comfort",
            )
            KvadrantListItem(
                title = "Trubarjeva cesta 2",
                subtitle = "27 aug · 08:12 · cancelled",
            )

            Column(Modifier.padding(horizontal = metrics.margin)) {
                Spacer(Modifier.height(metrics.margin))
                KvadrantText("button — reads `mediumLarge` (19/300 here) · kit: button 15/400", style = meta)
                KvadrantButton(text = "order comfort", onClick = {})
                Spacer(Modifier.height(metrics.margin))

                KvadrantText(
                    "text box — reads `mediumLarge` (19/300 here) · kit: address search, light field",
                    style = meta,
                )
                KvadrantTextBox(
                    value = "",
                    onValueChange = {},
                    placeholder = "where to?",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(metrics.margin))

                KvadrantText("tile label — reads `normal` (15/400) · kit: tile label 19/300", style = meta)
                Row(verticalAlignment = Alignment.Bottom) {
                    KvadrantTile(size = TileSize.Small) {
                        KvadrantText(
                            "today",
                            Modifier.align(Alignment.BottomStart).padding(6.dp),
                            style = KvadrantTheme.typography.normal.copy(color = colors.onAccent),
                        )
                    }
                    Spacer(Modifier.padding(metrics.tileGap / 2))
                    KvadrantTile(size = TileSize.Medium) {
                        KvadrantText(
                            "$ 4 280",
                            Modifier.align(Alignment.TopStart).padding(6.dp),
                            style = ShashkiTheme.typography.figure.copy(color = colors.onAccent),
                        )
                        KvadrantText(
                            "11 trips",
                            Modifier.align(Alignment.BottomStart).padding(6.dp),
                            style = KvadrantTheme.typography.normal.copy(color = colors.onAccent),
                        )
                    }
                }
            }
        }
    }
}
