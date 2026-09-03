package io.github.youndie.shashki.rider.feature.receipt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.LocalKompotDegradationSink
import io.github.youndie.shashki.rider.RECEIPT_SCREEN
import io.github.youndie.shashki.ui.screens.RiderReceipt
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

/**
 * R9·b, drawn from the server's own card (B-61).
 *
 * **The second server-driven screen, and the one that made the first mean something.** The promo
 * screen is made of kompot's stock vocabulary; this one is a `FareBreakdown` — this product's
 * component, with the kit's composition rules in its renderer — so the whole path is exercised:
 * declared in `:protocol`, built by a server that has no Compose, registered by KSP in two halves,
 * and drawn here.
 */
@Composable
public fun ReceiptScreen(
    rideId: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ReceiptViewModel = koinViewModel(key = rideId, parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The same reasoning as the promo screen's: what a degradation report is worth depends on the
    // screen it happened on, so the sink is provided per screen rather than around the application.
    val sink = koinInject<KompotDegradationSink>(qualifier = named(RECEIPT_SCREEN))
    CompositionLocalProvider(LocalKompotDegradationSink provides sink) {
        ReceiptContent(uiState, modifier, onBack)
    }
}

@Composable
public fun ReceiptContent(
    uiState: ReceiptUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    RiderReceipt(tree = uiState.tree, loading = uiState.loading, modifier = modifier, onBack = onBack)
}
