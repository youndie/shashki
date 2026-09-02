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

    EarningsContent(uiState, modifier)
}

@Composable
public fun EarningsContent(
    uiState: EarningsUiState,
    modifier: Modifier = Modifier,
) {
    DriverEarnings(
        titles = listOf("today", "week", "history"),
        today = uiState.today,
        todayLabel = if (uiState.loading) "" else "today",
        tiles = uiState.tiles,
        // **The per-ride history is not here** and the screen says so rather than drawing an empty
        // grid: the payout rows carry a ride id, and joining them to the ride's own row is the list
        // B-45 built for the rider. Doing it for the driver is that item's shape a second time.
        history = emptyList(),
        emptyLine = "one line per ride is not built yet",
        modifier = modifier,
    )
}
