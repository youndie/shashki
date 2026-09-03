package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.BackBar
import io.github.youndie.shashki.ui.kompot.ServerScreen

/**
 * R9·b: what a ride cost, drawn from the card the server composed (B-61).
 *
 * **The screen is a frame around somebody else's tree, and that is the whole design.** Everything
 * that decides what a receipt says — which lines, in which order, which of them is the figure — was
 * decided by whoever charged the card. This half knows the kit, the back bar and nothing about
 * money; there is no fare in this file and no arithmetic anywhere in it.
 *
 * It is drawn here rather than in `:rider` for the reason every other screen is: a fixture in this
 * module can photograph it, and a screen no golden covers is a screen whose design nobody reviews.
 */
@Composable
public fun RiderReceipt(
    tree: KompotComponent?,
    /**
     * Whether the answer is still coming.
     *
     * **A ride with no receipt and a receipt that failed to arrive look identical here, on purpose.**
     * A ride still running has none — the server answers 404 and means it — and a screen that
     * claimed to tell that apart from a network failure would be inventing the difference. What is
     * worth saying is only whether anything is still expected.
     */
    loading: Boolean,
    modifier: Modifier = Modifier,
    /** How to leave, or `null` where the platform already offers it (B-67). */
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().background(KvadrantTheme.colors.background)) {
        if (tree != null) {
            ServerScreen(tree, Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                KvadrantText(
                    if (loading) "…" else "no receipt for this ride yet",
                    style = ShashkiTheme.typography.stateHeadline.copy(color = KvadrantTheme.colors.subtle),
                )
            }
        }

        onBack?.let { BackBar(it) }
    }
}
