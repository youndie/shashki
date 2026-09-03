package io.github.youndie.shashki.driver.feature.earnings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.ui.screens.DriverEarnings
import org.koin.compose.viewmodel.koinViewModel

/** D6, with the payout sums behind it. */
@Composable
public fun EarningsScreen(
    onFailed: (String) -> Unit,
    /** How to leave, or `null` where the platform offers it (B-67). */
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: EarningsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EarningsUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    EarningsContent(uiState, modifier, onBack)
}

@Composable
public fun EarningsContent(
    uiState: EarningsUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    DriverEarnings(
        titles = listOf("today", "week", "history"),
        today = uiState.today,
        todayLabel = if (uiState.loading) "" else "today",
        tiles = uiState.tiles,
        // **By day, not by ride** (B-81): the kit's D6 lists payouts as `28 aug · 11 trips · 4 280 ₽`,
        // which is the payout rows grouped by the server's day and named by this calendar.
        history = uiState.history,
        emptyLine = if (uiState.loading) "" else "no rides yet",
        modifier = modifier,
        onBack = onBack,
    )
}
