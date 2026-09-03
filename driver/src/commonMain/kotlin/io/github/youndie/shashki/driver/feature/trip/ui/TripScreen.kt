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
                status = ride?.status?.asWord() ?: "…",
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
 * The state a driver is in, as a word (B-68).
 *
 * **Three of the four read as prose by luck and the fourth did not.** The header printed
 * `RideStatus.name.lowercase()`, so `assigned`, `arriving` and `arrived` looked deliberate while
 * `in_progress` arrived with its underscore on a screen whose headings are lower-case prose. A
 * status is a wire value and a label is a sentence; they coincided until they did not.
 *
 * The `else` branch is the guard rather than laziness: the next state anybody adds gets spaces
 * instead of underscores while somebody decides what it should say, and `EveryStateReadsAsAWordTest`
 * fails if that is the branch it lands in.
 */
internal fun RideStatus.asWord(): String =
    when (this) {
        RideStatus.ASSIGNED -> "assigned"
        RideStatus.ARRIVING -> "on the way"
        RideStatus.ARRIVED -> "at the pickup"
        RideStatus.IN_PROGRESS -> "on the trip"
        RideStatus.COMPLETED -> "finished"
        RideStatus.CANCELLED -> "cancelled"
        else -> name.lowercase().replace('_', ' ')
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
