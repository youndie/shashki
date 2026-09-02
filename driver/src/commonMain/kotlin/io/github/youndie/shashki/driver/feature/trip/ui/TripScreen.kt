package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    DriverTripContent(uiState, modifier)
}

/** Stateless. */
@Composable
public fun DriverTripContent(
    uiState: DriverTripUiState,
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
            ),
        modifier = modifier,
    )
}
