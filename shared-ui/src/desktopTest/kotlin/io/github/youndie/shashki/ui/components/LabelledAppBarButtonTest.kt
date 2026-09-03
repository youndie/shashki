package io.github.youndie.shashki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The words are the target, not only the ring** (B-71).
 *
 * Pressed by its text on purpose: that is what a rider does with *order · $ 28.96*, and what a tester
 * did twice on the stand before finding the 48 dp circle beside it. A test that pressed the ring
 * would pass on the old drawing too.
 */
@OptIn(ExperimentalTestApi::class)
class LabelledAppBarButtonTest {
    @Test
    fun `pressing the label fires the action`() =
        runComposeUiTest {
            var pressed = 0
            setContent {
                Themed { LabelledAppBarButton("order · $ 28.96", ShashkiIcons.check, onClick = { pressed++ }) }
            }

            onNodeWithText("order · $ 28.96").performClick()

            assertEquals(1, pressed)
        }

    @Test
    fun `a disabled bar takes no tap on its words either`() =
        runComposeUiTest {
            var pressed = 0
            setContent {
                Themed {
                    LabelledAppBarButton("no cars nearby", ShashkiIcons.check, onClick = { pressed++ }, enabled = false)
                }
            }

            onNodeWithText("no cars nearby").assertIsNotEnabled().performClick()

            assertEquals(0, pressed, "B-62's 'nothing to order' must stay nothing to press")
        }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val latin = kvadrantLatin()
        RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) { content() }
    }
}
