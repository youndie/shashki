package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.screens.DriverTripSummary
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** D5, with the read behind it (B-70). */
@Composable
public fun TripSummaryScreen(
    rideId: String,
    onBackToShift: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripSummaryViewModel = koinViewModel(parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TripSummaryContent(uiState, onBackToShift, modifier)
}

/** Stateless. */
@Composable
public fun TripSummaryContent(
    uiState: TripSummaryUiState,
    onBackToShift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = uiState.summary
    if (summary != null) {
        DriverTripSummary(summary, onBackToShift, modifier)
        return
    }

    // The kit's loading rule: no skeleton, no spinner — dots while the answer is coming, and a line
    // that names what is slow once it is not coming. The settlement is a saga and can be a moment
    // behind the trip's end; "not yet" and "not at all" are told apart by whether we are still asking.
    Box(
        modifier.fillMaxSize().background(KvadrantTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        KvadrantText(
            if (uiState.loading) "…" else "the payout is not written down yet",
            style = ShashkiTheme.typography.stateHeadline.copy(color = KvadrantTheme.colors.subtle),
        )
    }
}
