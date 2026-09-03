package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.asMoney
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
    val leg = uiState.ride?.leg

    RiderTripInProgress(
        scene = uiState.scene,
        stage = uiState.stage,
        // **While the car is on its way the figure is the minutes to it, not the journey's length**
        // (B-76). `leg` is the server's road from the driver's last position to the pickup; when the
        // server has none — no position yet — the words stand in, as they did before.
        // **During the trip the figure is the minutes left, the meta the arrival and the distance
        // left, and the fare is shown once** (B-77) — the kit's `18 min / arriving 20:06 · 11.2 km
        // left / 420 ₽`. Both numbers are the server's leg to the drop-off; the clock is the view
        // model's, from the rider's own watch. Without a leg the screen falls back to the quote.
        headline =
            when (uiState.stage) {
                TripStage.ARRIVING -> leg?.durationSeconds?.asDuration() ?: "on its way"
                TripStage.ARRIVED -> "waiting for you"
                TripStage.IN_PROGRESS -> leg?.durationSeconds?.asDuration() ?: "airport"
            },
        meta =
            when {
                uiState.stage == TripStage.ARRIVING && leg != null -> {
                    "${leg.distanceMetres.asDistance()} to you"
                }

                uiState.stage == TripStage.IN_PROGRESS && leg != null -> {
                    listOfNotNull(uiState.arrivingAt?.let { "arriving $it" }, "${leg.distanceMetres.asDistance()} left")
                        .joinToString(" · ")
                }

                else -> {
                    quote
                        ?.let { "${it.durationSeconds.asDuration()} · ${it.distanceMetres.asDistance()}" }
                        .orEmpty()
                }
            },
        fare = quote?.asMoney()?.takeIf { uiState.stage == TripStage.IN_PROGRESS },
        // R7·a, the kit's words with this ride's number in them (B-80). The fare is held at the last
        // confirmed point: that is what the settlement does, and the band says so.
        gpsLost =
            uiState.quietForSeconds?.let {
                "gps lost · last position $it seconds ago. " +
                    "The trip is running and the fare is held at the last confirmed point."
            },
        // **The record, since B-63 gave the server one.** This was four dashes and an identifier —
        // the honest shape while `RideView` carried nothing about the person, and the note said so.
        // The dashes are still what a driver with no record gets: the plate is the field a rider
        // checks a real car against, and a blank is better there than a guess.
        driver =
            uiState.ride?.driver.let { known ->
                TripDriver(
                    name = known?.name ?: "—",
                    car = known?.car ?: "—",
                    plate = known?.plate ?: "—",
                    rating = known?.rating?.let { rating -> "${(rating * 10).toInt() / 10.0}" } ?: "—",
                    carRects = 2,
                )
            },
        actionLabel = "call the driver",
        onCall = { onAction(TripUiAction.Call) },
        prompt = uiState.takeIf { it.confirming }?.let { cancelPrompt(it.ride) },
        onConfirmPrompt = { onAction(TripUiAction.ConfirmCancel) },
        onDismissPrompt = { onAction(TripUiAction.DismissConfirm) },
        onCancel = { onAction(TripUiAction.Cancel) },
        modifier = modifier,
    )
}
