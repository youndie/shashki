package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.rider.format.asDistance
import io.github.youndie.shashki.rider.format.asDuration
import io.github.youndie.shashki.rider.format.asMoney
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.screens.RideClassOffer
import io.github.youndie.shashki.ui.screens.RiderClassPicker
import org.koin.compose.viewmodel.koinViewModel

/**
 * R4, with a view model behind it.
 *
 * **The split is the project's rule and it pays for itself immediately**: `RiderClassPicker` in
 * `:shared-ui` already draws this screen from plain values and is photographed by viddik that way.
 * Everything the server is involved in lives here; the drawing has never heard of Koin.
 */
@Composable
public fun ClassPickerScreen(
    scene: MapScene,
    onOrdered: (rideId: String) -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassPickerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ClassPickerUiEvent.Ordered -> onOrdered(event.rideId)
                is ClassPickerUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    ClassPickerContent(scene, uiState, viewModel::onAction, modifier)
}

/** Stateless: `uiState` and `onAction` and nothing else. A preview or a golden needs no graph. */
@Composable
public fun ClassPickerContent(
    scene: MapScene,
    uiState: ClassPickerUiState,
    onAction: (ClassPickerUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = uiState.quotes.firstOrNull { it.rideClass == uiState.selected }

    RiderClassPicker(
        scene = scene,
        destination = uiState.destination,
        destinationMeta =
            if (uiState.loading) {
                "…"
            } else {
                "${uiState.distanceMetres.asDistance()} · ${uiState.durationSeconds.asDuration()}"
            },
        offers = RideClass.entries.map { uiState.offerFor(it) },
        selectedIndex = RideClass.entries.indexOf(uiState.selected),
        paymentLabel = "card ·· 4417",
        orderLabel = order?.let { "order · ${it.quote.asMoney()}" } ?: "order",
        onSelect = { index -> onAction(ClassPickerUiAction.Select(RideClass.entries[index])) },
        onChangePayment = { },
        onOrder = { onAction(ClassPickerUiAction.Order) },
        modifier = modifier,
    )
}

/**
 * A class the server did not price is drawn as unavailable rather than omitted.
 *
 * The kit's tile has a state for exactly this — "no cars nearby", an em dash where the price is —
 * and dropping the row instead would make the list reflow while the rider was reading it.
 */
private fun ClassPickerUiState.offerFor(rideClass: RideClass): RideClassOffer {
    val quote = quotes.firstOrNull { it.rideClass == rideClass }
    val carRects = RideClass.entries.indexOf(rideClass) + 1
    return RideClassOffer(
        name = rideClass.name.lowercase(),
        // **The kit puts the wait and the car here — "4 min · Kia Rio" — and the server can answer
        // neither.** The wait is a route from the nearest candidate driver to the pickup, which is a
        // query the server does not expose; the car is the assigned driver's, and nobody is assigned
        // yet. So this is a dash, on the same rule as the trip screen's blank registration: a number
        // in the wrong place reads as an answer, and a dash reads as a question.
        meta = if (quote == null) "no cars nearby" else "—",
        price = quote?.quote?.asMoney(),
        carRects = carRects,
        available = quote != null,
    )
}
