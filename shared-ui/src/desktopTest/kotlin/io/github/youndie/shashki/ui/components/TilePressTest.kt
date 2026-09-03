package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Pressing a tile moves the tile, not only the words on it** (B-82).
 *
 * `KvadrantTheme` provides the tilt as `LocalIndication`, so every `clickable` in this product was
 * already *asking* for it — and nothing moved, because the indication node draws what is **after**
 * it in the chain and every surface here was written `.background(…).clickable(…)`. The colour
 * stayed put while the label tilted inside it, which on a 54 dp row is invisible.
 *
 * So this presses a tile and compares the pixels **at its corner**, which is surface and never text:
 * with the click outside the background the corner moves, with it inside the corner cannot.
 */
@OptIn(ExperimentalTestApi::class)
class TilePressTest {
    @Test
    fun `a pressed tile moves its own surface`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                val latin = kvadrantLatin()
                RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
                    Box(Modifier.size(240.dp).background(Color.Black).padding(20.dp)) {
                        Box(Modifier.testTag("tile")) {
                            ClassTile(
                                name = "economy",
                                meta = "here · Skoda Octavia",
                                price = "$ 28.96",
                                state = ClassTileState.Selected,
                                carRects = 1,
                                onClick = {},
                            )
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(FRAME)
            val resting = onNodeWithTag("tile").captureToImage().toPixelMap()

            onNodeWithTag("tile").performTouchInput { down(center) }
            mainClock.advanceTimeBy(PRESS)
            val pressed = onNodeWithTag("tile").captureToImage().toPixelMap()

            var corner = 0
            for (y in 0 until CORNER) {
                for (x in 0 until CORNER) {
                    if (resting[x, y] != pressed[x, y]) corner++
                }
            }

            // **Measured, both ways round**: with the click outside the colour 35 of these 36 pixels
            // move; with it inside — the arrangement every surface here used to have — 8 do. Half is
            // a threshold neither arrangement lands on by accident.
            assertTrue(
                corner > CORNER * CORNER / 2,
                "only $corner of ${CORNER * CORNER} corner pixels moved: the tilt is drawing inside the surface again",
            )
        }

    private companion object {
        const val FRAME = 32L
        const val PRESS = 200L
        const val CORNER = 6
    }
}
