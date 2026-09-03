package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.components.KvadrantAppBarButton
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * The kit's action row: the library's ring with a label **beside** it, and the two are one target
 * (B-71).
 *
 * `KvadrantAppBarButton` puts its label underneath and centres the pair; the kit's R4, R7 and D3
 * bars put the label to the right, so these screens drew the ring through the library and the label
 * beside it themselves — and only the ring took the tap. A rider who hit *order · $ 28.96* on the
 * words saw nothing happen, and so did the tester who found this. The row is the control now: the
 * words are as pressable as the ring, and the ring keeps the library's 48 dp target.
 *
 * `enabled = false` greys both halves and takes no tap on either, which is the picker's "nothing to
 * order" (B-62).
 */
@Composable
public fun LabelledAppBarButton(
    label: String,
    glyph: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The ink when enabled; the kit's foreground unless a screen says otherwise. */
    tint: Color = Color.Unspecified,
) {
    val colors = KvadrantTheme.colors
    val ink = if (!enabled) colors.disabled else tint.takeOrElse { colors.foreground }
    Row(
        // The row is one control with the label as its name, which is also what a screen reader
        // announces and what a test presses by.
        modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The ring stays the library's, for its 36 dp visual and 48 dp target — and its own click
        // fires the same callback, so a tap inside the ring is not swallowed by the row's.
        KvadrantAppBarButton(onClick = onClick, enabled = enabled, label = null) {
            Image(
                painter = rememberVectorPainter(glyph),
                contentDescription = null,
                modifier = Modifier.size(GLYPH).align(Alignment.Center),
                colorFilter = ColorFilter.tint(ink),
            )
        }
        KvadrantText(label, style = ShashkiTheme.typography.body.copy(color = ink))
    }
}

private val GAP = 12.dp
private val GLYPH = 20.dp
