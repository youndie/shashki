package io.github.youndie.shashki.driver.feature.shift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.ui.format.asCoordinates
import io.github.youndie.shashki.ui.format.asDistance
import io.github.youndie.shashki.ui.format.asDuration
import io.github.youndie.shashki.ui.format.asMoney
import io.github.youndie.shashki.ui.screens.DriverOfferState
import io.github.youndie.shashki.ui.screens.DriverShift
import io.github.youndie.shashki.ui.screens.DriverShiftState
import org.koin.compose.viewmodel.koinViewModel

/**
 * D1, with the socket behind it.
 *
 * Stateful half: the view model, its events, and nothing drawn. [ShiftContent] is what a golden
 * photographs.
 */
@Composable
public fun ShiftScreen(
    onAccepted: (rideId: String) -> Unit,
    onGone: () -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShiftViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ShiftUiEvent.Accepted -> onAccepted(event.rideId)
                ShiftUiEvent.Gone -> onGone()
                is ShiftUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    ShiftContent(uiState, viewModel::onAction, modifier)
}

/** Stateless: `uiState` and `onAction`. No graph, no socket, no server. */
@Composable
public fun ShiftContent(
    uiState: ShiftUiState,
    onAction: (ShiftUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DriverShift(
        state =
            DriverShiftState(
                online = uiState.online,
                driverLabel = uiState.driverLabel,
                classLabel =
                    uiState.offer
                        ?.rideClass
                        ?.name
                        ?.lowercase() ?: "on shift",
                reported = uiState.reported.takeIf { uiState.online },
                offer = uiState.offer?.asOfferState(uiState.secondsLeft, uiState.secondsTotal),
            ),
        onToggleOnline = { onAction(ShiftUiAction.ToggleOnline) },
        onAccept = { onAction(ShiftUiAction.Accept) },
        onDecline = { onAction(ShiftUiAction.Decline) },
        modifier = modifier,
    )
}

/**
 * The offer, as words.
 *
 * **The pickup's meta is a dash and that is the honest value.** The kit draws "4 min · 1.2 km"
 * there — how far the *driver* is from the pickup — and the server answers no such question: the
 * quote it sends is the rider's journey, pickup to dropoff. A number borrowed from the wrong leg
 * would read as an answer. The rider's class picker takes the same decision for the same reason.
 */
private fun OfferView.asOfferState(
    secondsLeft: Int,
    secondsTotal: Int,
): DriverOfferState =
    DriverOfferState(
        fare = quote.asMoney(),
        classAndPayment = "${rideClass.name.lowercase()} · card",
        secondsLeft = secondsLeft,
        secondsTotal = secondsTotal,
        pickup = pickup.asCoordinates(),
        pickupMeta = "—",
        dropoff = dropoff.asCoordinates(),
        dropoffMeta = "${quote.distanceMetres.asDistance()} · ${quote.durationSeconds.asDuration()}",
    )
