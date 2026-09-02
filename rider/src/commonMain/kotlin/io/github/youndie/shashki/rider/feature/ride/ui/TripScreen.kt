package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.rider.format.asDistance
import io.github.youndie.shashki.rider.format.asDuration
import io.github.youndie.shashki.ui.screens.RiderTripInProgress
import io.github.youndie.shashki.ui.screens.TripDriver
import io.github.youndie.shashki.ui.screens.TripStage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The trip screen, with the ride behind it.
 *
 * The `rideId` comes from the route, which comes from the address bar — so a pasted `/trip/abc`
 * opens the right ride, which is what makes the address part of the interface rather than decoration.
 */
@Composable
public fun TripScreen(
    rideId: String,
    onFinished: () -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripViewModel = koinViewModel(parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TripUiEvent.Finished -> onFinished()
                is TripUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    TripContent(uiState, viewModel::onAction, modifier)
}

@Composable
public fun TripContent(
    uiState: TripUiState,
    onAction: (TripUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quote = uiState.ride?.quote

    RiderTripInProgress(
        scene = uiState.scene,
        stage = uiState.stage,
        headline =
            when (uiState.stage) {
                TripStage.ARRIVING -> "on its way"
                TripStage.ARRIVED -> "waiting for you"
                TripStage.IN_PROGRESS -> "airport"
            },
        meta =
            quote?.let { "${it.durationSeconds.asDuration()} · ${it.distanceMetres.asDistance()}" }.orEmpty(),
        // **A placeholder, and it is the one thing on this screen the server cannot answer.**
        // `RideView` carries a `driverId` and nothing about the person: no name, no car, no plate.
        // Inventing them here would put fiction on the screen the rider checks a registration
        // against, so they are visibly blank until the server has somewhere to put them.
        driver =
            TripDriver(
                name = uiState.ride?.driverId ?: "—",
                car = "—",
                plate = "—",
                rating = "—",
                carRects = 2,
            ),
        actionLabel = "call the driver",
        onCall = { onAction(TripUiAction.Call) },
        onCancel = { onAction(TripUiAction.Cancel) },
        modifier = modifier,
    )
}
