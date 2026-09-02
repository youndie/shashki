package io.github.youndie.shashki.rider.feature.promo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.LocalKompotDegradationSink
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.kompot.ServerScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The one screen the server owns.
 *
 * **It has no native version, and that is the decision rather than an omission** (research §2 D11).
 * A screen with a fallback would never exercise the thing a server-driven screen is for: a client
 * meeting a component it does not know, drawing the rest, and saying nothing about it to the person
 * looking at it.
 */
@Composable
public fun PromoScreen(
    onAction: KompotActionHandler,
    modifier: Modifier = Modifier,
    viewModel: PromoViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // **The sink is provided here rather than around the whole application**, because what it reports
    // is useless without the screen it happened on — "a client could not draw `earningsTile`" is a
    // fact; "on the promo screen" is what makes it actionable. This is the only server-driven screen
    // there is, so this is the only place that provides one (B-39).
    CompositionLocalProvider(LocalKompotDegradationSink provides koinInject<KompotDegradationSink>()) {
        PromoContent(uiState, onAction, modifier)
    }
}

@Composable
public fun PromoContent(
    uiState: PromoUiState,
    onAction: KompotActionHandler,
    modifier: Modifier = Modifier,
) {
    val tree = uiState.tree
    if (tree != null) {
        ServerScreen(tree, modifier, onAction)
        return
    }

    // Nothing to show, and it says which nothing. A blank screen would be the same pixels for "still
    // asking" and "there is no promotion", and only one of those is worth waiting on.
    Box(
        modifier
            .fillMaxSize()
            .background(KvadrantTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        KvadrantText(
            if (uiState.loading) "…" else "nothing on offer today",
            style = ShashkiTheme.typography.stateHeadline.copy(color = KvadrantTheme.colors.subtle),
        )
    }
}
