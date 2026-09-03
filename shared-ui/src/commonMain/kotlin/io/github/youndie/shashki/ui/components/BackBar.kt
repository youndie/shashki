package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.components.KvadrantAppBarButton
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons

/**
 * The one control a window needs and a browser does not (B-67).
 *
 * **A screen that can be entered has to be leavable.** In a browser that is the back button people
 * already have; in a window the back stack is push-only, so opening the driver's documents meant
 * restarting the process to see the shift again — measured by trying it.
 *
 * It is the kit's application bar and not an invention: 54 dp of chrome with a ring button in it,
 * `KvadrantAppBarButton`'s own 36 dp visual and 48 dp touch target. The screens that already end in a
 * bar — R8's *done*, the trip's *call the driver* — do not get a second one; this is for the three
 * that end in nothing.
 */
@Composable
public fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KvadrantTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(KvadrantTheme.metrics.appBarHeight)
            .background(colors.chrome)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvadrantAppBarButton(onClick = onBack, label = null) {
            Image(
                painter = rememberVectorPainter(ShashkiIcons.back),
                contentDescription = null,
                modifier = Modifier.size(GLYPH).align(Alignment.Center),
                colorFilter = ColorFilter.tint(colors.foreground),
            )
        }
    }
}

private val GLYPH = 26.dp
