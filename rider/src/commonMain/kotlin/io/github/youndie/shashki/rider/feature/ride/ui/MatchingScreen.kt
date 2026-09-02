package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.ui.format.asDistance
import io.github.youndie.shashki.ui.format.asDuration
import io.github.youndie.shashki.ui.screens.MatchingStage
import io.github.youndie.shashki.ui.screens.RiderMatching
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The wait, with the ride behind it.
 *
 * `rideId` comes from the route: `/matching/{id}` is a real address, so a reload while a rider waits
 * comes back to the wait rather than to the picker.
 */
@Composable
public fun MatchingScreen(
    rideId: String,
    onAssigned: (String) -> Unit,
    onBack: () -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchingViewModel = koinViewModel(parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MatchingUiEvent.Assigned -> onAssigned(event.rideId)
                MatchingUiEvent.Back -> onBack()
                is MatchingUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    MatchingContent(uiState, viewModel::onAction, modifier)
}

/**
 * **The copy is here and the numbers are the server's.** How long the journey is comes from the
 * quote; what cancelling costs comes from `cancellationFeeCents`, which the server computes from the
 * same `Commission` the settlement charges. The only thing this file decides is the wording around
 * them — including the one place the wording changes with the number, which is R10 before and after
 * a driver has set off.
 */
@Composable
public fun MatchingContent(
    uiState: MatchingUiState,
    onAction: (MatchingUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quote = uiState.ride?.quote
    val looking = uiState.stage == MatchingStage.LOOKING

    RiderMatching(
        stage = uiState.stage,
        headline = if (looking) "looking for a car" else "no cars nearby",
        supporting =
            if (looking) {
                "asking the drivers around you"
            } else {
                // No "notify me" and no "we will keep trying": both would be promises this build
                // does not keep. What it can offer is the same order again.
                "nobody is driving here right now"
            },
        destination = "airport",
        meta =
            listOfNotNull(
                quote?.distanceMetres?.asDistance(),
                quote?.durationSeconds?.asDuration(),
            ).joinToString(" · "),
        actionLabel = if (looking) "cancel" else "try again",
        onAction = { onAction(MatchingUiAction.Act) },
        prompt = uiState.takeIf { it.confirming }?.let { cancelPrompt(it.ride) },
        onConfirmPrompt = { onAction(MatchingUiAction.ConfirmCancel) },
        onDismissPrompt = { onAction(MatchingUiAction.DismissConfirm) },
        modifier = modifier,
    )
}
