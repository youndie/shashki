package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A pressable surface is still a control** (B-82).
 *
 * The kit's press feedback is an *indication*, so it is applied through `clickable` and the role,
 * the enabled state and the click action are Compose's own. The library's `kvadrantTilt` takes an
 * `onClick` of its own and carries none of them — a `performClick` on a node wearing it does
 * nothing, which was measured before this helper was written. These two are why the helper is
 * `clickable` with a colour under it and not that.
 */
@OptIn(ExperimentalTestApi::class)
class PressableTest {
    @Test
    fun `a pressable surface clicks`() =
        runComposeUiTest {
            var fired = 0
            setContent {
                Box(Modifier.size(120.dp).pressableSurface(Color.Blue) { fired++ }) { KvadrantText("press me") }
            }

            onNodeWithText("press me").performClick()

            assertEquals(1, fired)
        }

    @Test
    fun `a disabled one is disabled, and says so`() =
        runComposeUiTest {
            var fired = 0
            setContent {
                Box(Modifier.size(120.dp).pressableSurface(Color.Blue, enabled = false) { fired++ }) {
                    KvadrantText("press me")
                }
            }

            onNodeWithText("press me").assertIsNotEnabled().performClick()

            assertEquals(0, fired)
        }
}
