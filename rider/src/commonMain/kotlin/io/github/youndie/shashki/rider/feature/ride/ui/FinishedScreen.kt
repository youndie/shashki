package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.money
import io.github.youndie.shashki.ui.screens.RiderFinished
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** R8, with the finished ride behind it. `/finished/{id}` is an address like every other screen's. */
@Composable
public fun FinishedScreen(
    rideId: String,
    onDone: () -> Unit,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinishedViewModel = koinViewModel(parameters = { parametersOf(rideId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FinishedUiEvent.Done -> onDone()
                is FinishedUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    FinishedContent(uiState, viewModel::onAction, modifier)
}

@Composable
public fun FinishedContent(
    uiState: FinishedUiState,
    onAction: (FinishedUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ride = uiState.ride
    val currency = ride?.quote?.currency ?: "USD"

    RiderFinished(
        // **What was taken, and the quote only until the settlement has said** — the two are the
        // same for a fare and differ by three quarters for a cancellation.
        total =
            ride?.chargedCents?.let { money(it, currency) }
                ?: ride?.quote?.let { money(it.amountCents, currency) }.orEmpty(),
        destination = listOfNotNull("airport", ride?.quote?.distanceMetres?.asDistance()).joinToString(" · "),
        // **A person, since there is a record to read one from** (B-63). This was the driver's id
        // — on a stand where a rider and a driver are the same account, R8 asked somebody to rate
        // their own e-mail address.
        driver = ride?.driver?.name ?: "your driver",
        stars = uiState.stars,
        tips = FinishedUiState.TIPS.map { money(it, currency) },
        selectedTip = uiState.selectedTip,
        doneLabel = if (uiState.sending) "sending" else "done",
        onStars = { onAction(FinishedUiAction.Stars(it)) },
        onTip = { onAction(FinishedUiAction.Tip(it)) },
        onDone = { onAction(FinishedUiAction.Done) },
        modifier = modifier,
        skipped = uiState.skipped,
        // **The card and the journey, the kit's own second line** (B-59). The payment method is the
        // id the request carried — this product has no card, and printing digits it does not have
        // would be a fabrication.
        meta =
            listOfNotNull(
                ride?.paymentMethodId?.let { "paid with $it" },
                ride?.quote?.durationSeconds?.asDuration(),
            ).joinToString(" · ").takeIf { it.isNotBlank() },
        totalWithTip = uiState.totalWithTipCents()?.let { money(it, currency) },
    )
}
