package io.github.youndie.shashki.ui.kompot

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.DividerComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.protocol.FareBreakdown
import io.github.youndie.shashki.protocol.FareLine
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.protocol.TripRow
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **What a screen reader is handed, read off the semantics tree** (#43).
 *
 * kompot 0.38 gave its own vocabulary roles, labels and headings (SPEC.md §4.11); this product's
 * three components are drawn by renderers here, so making them readable is this module's job. The
 * property is that one fact is one stop: a fare line is "tip, $ 3" and not a label in one place and
 * a value somewhere after it. Nothing on screen changes, which is why no golden can say it.
 *
 * The check is a node carrying *both* texts. Without the merge each text is its own node and no
 * single node matches — so removing a `semantics(mergeDescendants = true)` turns this red.
 */
@OptIn(ExperimentalTestApi::class)
class RendererSemanticsTest {
    @Test
    fun `a fare line, a trip row and a tile are each read as one`() =
        runComposeUiTest {
            setContent {
                val latin = kvadrantLatin()
                RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
                    ServerScreen(TREE)
                }
            }

            onNode(hasText("tip") and hasText("$ 3")).assertExists("the fare line is two stops")
            onNode(hasText("Prešernov trg") and hasText("airport") and hasText("$ 26"))
                .assertExists("the row is in pieces")
            onNode(hasText("trips") and hasText("12")).assertExists("the tile is two stops")
        }

    /** The title the server marks as a heading reaches the tree as one — kompot's half of the same (#43). */
    @Test
    fun `the heading the server names is a heading`() =
        runComposeUiTest {
            setContent {
                val latin = kvadrantLatin()
                RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
                    ServerScreen(TREE)
                }
            }

            val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
            assertEquals(
                listOf(listOf("receipt")),
                headings.map { node -> node.config[SemanticsProperties.Text].map { it.text } },
            )
        }

    private companion object {
        val TREE =
            ColumnComponent(
                id = "root",
                spacing = 16,
                children =
                    listOf(
                        TextComponent(
                            id = "title",
                            text = "receipt",
                            style = TypographyToken(ShashkiTokens.TYPE_PAGE_TITLE),
                            heading = true,
                        ),
                        FareBreakdown(
                            id = "card",
                            amount = "$ 31.96",
                            caption = "economy",
                            primary = true,
                            lines = listOf(FareLine("fare", "$ 28.96"), FareLine("tip", "$ 3")),
                        ),
                        DividerComponent(id = "rule"),
                        TripRow(
                            id = "row",
                            title = "a ride",
                            meta = "2 september",
                            amount = "$ 26",
                            from = "Prešernov trg",
                            to = "airport",
                        ),
                        EarningsTile(id = "tile", label = "trips", figure = "12", size = 2),
                    ),
            )
    }
}
