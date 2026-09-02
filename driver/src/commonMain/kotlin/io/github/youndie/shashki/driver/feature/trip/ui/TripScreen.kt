package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.ui.format.asCoordinates
import io.github.youndie.shashki.ui.format.asDistance
import io.github.youndie.shashki.ui.format.asDuration
import io.github.youndie.shashki.ui.format.asMoney
import io.github.youndie.shashki.ui.screens.DriverAssignedRide
import io.github.youndie.shashki.ui.screens.DriverAssignedRideState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** D2, with the poll behind it. */
@Composable
public fun DriverTripScreen(
    rideId: String,
    onFinished: () -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriverTripViewModel = koinViewModel(parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DriverTripUiEvent.Finished -> onFinished()
                is DriverTripUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    DriverTripContent(uiState, viewModel::onAction, modifier)
}

/** Stateless. */
@Composable
public fun DriverTripContent(
    uiState: DriverTripUiState,
    onAction: (DriverTripUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ride = uiState.ride
    DriverAssignedRide(
        state =
            DriverAssignedRideState(
                status = ride?.status?.name?.lowercase() ?: "…",
                fare = ride?.quote?.asMoney() ?: "—",
                pickup = ride?.pickup?.asCoordinates() ?: "—",
                dropoff = ride?.dropoff?.asCoordinates() ?: "—",
                legMeta =
                    ride?.quote?.let {
                        "${it.distanceMetres.asDistance()} · ${it.durationSeconds.asDuration()}"
                    } ?: "—",
                action = uiState.next?.asAction(),
                working = uiState.advancing,
            ),
        onAdvance = { onAction(DriverTripUiAction.Advance) },
        modifier = modifier,
    )
}

/**
 * The next state, as something a driver would tap.
 *
 * **The words are the driver's and the states are the server's.** `IN_PROGRESS` is a status; "start
 * the trip" is what somebody does. Keeping the mapping here rather than on the wire is why
 * `TripProgression` can stay a list of statuses that both halves agree on.
 */
private fun RideStatus.asAction(): String =
    when (this) {
        RideStatus.ARRIVING -> "on my way"
        RideStatus.ARRIVED -> "I am here"
        RideStatus.IN_PROGRESS -> "start the trip"
        RideStatus.COMPLETED -> "finish"
        else -> name.lowercase()
    }
