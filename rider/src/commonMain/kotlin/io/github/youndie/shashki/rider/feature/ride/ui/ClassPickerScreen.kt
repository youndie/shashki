package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.ui.format.asDistance
import io.github.youndie.shashki.ui.format.asDuration
import io.github.youndie.shashki.ui.format.asMoney
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
    /** The bar's overflow: the rider's own pages (B-45). */
    onMore: () -> Unit = {},
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

    ClassPickerContent(scene, uiState, viewModel::onAction, modifier, onMore)
}

/** Stateless: `uiState` and `onAction` and nothing else. A preview or a golden needs no graph. */
@Composable
public fun ClassPickerContent(
    scene: MapScene,
    uiState: ClassPickerUiState,
    onAction: (ClassPickerUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onMore: () -> Unit = {},
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
        // **And the bar does not offer what the tiles refuse** (B-62). A selected class with no
        // candidate is a class nobody can order; the bar says so in the kit's own words rather than
        // quoting a price for it, and the button below is disabled.
        orderLabel =
            when {
                order == null -> "order"
                order.pickupEtaSeconds == null -> "no cars nearby"
                else -> "order · ${order.quote.asMoney()}"
            },
        canOrder = order?.pickupEtaSeconds != null,
        onSelect = { index -> onAction(ClassPickerUiAction.Select(RideClass.entries[index])) },
        onChangePayment = { },
        onOrder = { onAction(ClassPickerUiAction.Order) },
        modifier = modifier,
        onMore = onMore,
    )
}

/**
 * A class the server did not price is drawn as unavailable rather than omitted.
 *
 * The kit's tile has a state for exactly this — "no cars nearby", an em dash where the price is —
 * and dropping the row instead would make the list reflow while the rider was reading it.
 */
internal fun ClassPickerUiState.offerFor(rideClass: RideClass): RideClassOffer {
    val quote = quotes.firstOrNull { it.rideClass == rideClass }
    val carRects = RideClass.entries.indexOf(rideClass) + 1
    val eta = quote?.pickupEtaSeconds
    return RideClassOffer(
        name = rideClass.name.lowercase(),
        // **The kit puts the wait and the car here — "4 min · Kia Rio" — and the server can now
        // answer the first** (B-31): the nearest candidate of this class, routed to the pickup.
        //
        // The car is still a dash and stays one. `RideView` carries a `driverId` and nothing about
        // the vehicle, and the registration is the field a rider checks a real car against — fiction
        // there is worse than a blank. Same rule as the trip screen's.
        meta = if (eta == null) "no cars nearby" else eta.asDuration(),
        // **No car, no price** (B-62). The tile has always drawn `—` where a price is missing; this
        // was handing it one anyway, so every class read `no cars nearby · $ 28.96` — an offer the
        // product cannot honour, beside the sentence saying so. The quote is real arithmetic and
        // stays available to the order bar's own decision; what is not real is a ride.
        price = quote?.quote?.asMoney()?.takeIf { eta != null },
        carRects = carRects,
        // **Unavailable when there is no car, not when there is no price.** Pricing is arithmetic
        // and answers for every class; what a rider cannot do is order a class nobody is driving.
        available = quote != null && eta != null,
    )
}
