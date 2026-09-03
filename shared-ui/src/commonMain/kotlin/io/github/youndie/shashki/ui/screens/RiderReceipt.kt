package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    /**
     * When — `3 september · 09:44` — as the one native line above the server's card (B-79).
     *
     * A date is the client's to format (B-61), so it cannot be a text in the tree; it is drawn
     * here, at the page margin, in the meta brush, and the tree begins under it.
     */
    when_: String? = null,
) {
    val colors = KvadrantTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        when_?.let {
            KvadrantText(
                it,
                Modifier.padding(start = KvadrantTheme.metrics.margin, top = 16.dp, end = KvadrantTheme.metrics.margin),
                style = ShashkiTheme.typography.meta.copy(color = colors.subtle),
            )
        }
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
