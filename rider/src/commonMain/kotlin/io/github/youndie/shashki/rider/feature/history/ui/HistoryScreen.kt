package io.github.youndie.shashki.rider.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.ui.screens.RiderHistory
import org.koin.compose.viewmodel.koinViewModel

/** R9, with the rider's rides behind it. The third pivot item is the screen the server owns. */
@Composable
public fun HistoryScreen(
    onTrip: (String) -> Unit,
    /** Where a row goes once its ride is over: R9·b, the receipt (B-61). */
    onReceipt: (String) -> Unit,
    /** How to leave, or `null` where the platform offers it (B-67). */
    onBack: (() -> Unit)? = null,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
    promo: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    HistoryContent(uiState, onTrip, onReceipt, modifier = modifier, promo = promo, onBack = onBack)
}

@Composable
public fun HistoryContent(
    uiState: HistoryUiState,
    onTrip: (String) -> Unit,
    onReceipt: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    promo: @Composable () -> Unit = {},
) {
    RiderHistory(
        titles = listOf("trips", "profile", "promo"),
        months = uiState.months,
        // The kit's section 08: one line, the disabled brush, no action. **"Loading" is not an empty
        // list**, and the two look identical while meaning opposite things — so nothing is said until
        // the answer is in.
        emptyLine = if (uiState.loading) "" else "no trips yet",
        profile = uiState.profile,
        // **One callback out of the screen, two destinations behind it.** `RiderHistory` draws a
        // list and should not learn what a ride status is; which of the two a row opens is decided
        // here, where the statuses were read.
        onTrip = { rideId -> if (rideId in uiState.settled) onReceipt(rideId) else onTrip(rideId) },
        modifier = modifier,
        promo = promo,
        onBack = onBack,
    )
}
