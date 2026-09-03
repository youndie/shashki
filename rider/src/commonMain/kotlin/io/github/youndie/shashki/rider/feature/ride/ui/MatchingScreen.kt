package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.asMoney
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

    val search = uiState.ride?.search
    RiderMatching(
        stage = uiState.stage,
        headline = uiState.headline(),
        supporting =
            if (looking) {
                // The kit's `14 cars within 3 km`, from the count the cascade started with (B-73);
                // the sentence the screen had before, until the first driver has been asked.
                search?.let { "${it.carsNearby} ${if (it.carsNearby == 1) "car" else "cars"} nearby" }
                    ?: "asking the drivers around you"
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
        // `asking the closest first · 0:24` and `economy · $ 28.96` — R5's second and third lines,
        // both the server's numbers: which driver is being asked, and what was ordered (B-73).
        progress =
            search?.takeIf { looking }?.let { s ->
                val clock = uiState.secondsLeft?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
                listOfNotNull(
                    if (s.asked ==
                        1
                    ) {
                        "asking the closest first"
                    } else {
                        "asking the ${s.asked.ordinal()} closest"
                    },
                    clock,
                ).joinToString(" · ")
            },
        order =
            uiState.ride?.takeIf { looking }?.let { ride ->
                listOfNotNull(ride.rideClass.name.lowercase(), quote?.asMoney()).joinToString(" · ")
            },
        actionLabel = if (looking) "cancel" else "try again",
        onAction = { onAction(MatchingUiAction.Act) },
        prompt = uiState.takeIf { it.confirming }?.let { cancelPrompt(it.ride) },
        onConfirmPrompt = { onAction(MatchingUiAction.ConfirmCancel) },
        onDismissPrompt = { onAction(MatchingUiAction.DismissConfirm) },
        modifier = modifier,
    )
}

/**
 * What the screen says when the search has ended (B-58).
 *
 * **The server's own sentence, when it sent one.** This used to print "no cars nearby" for every
 * ended search, which is the client asserting a reason it does not know: a ride refused for any
 * other reason said the same thing, and the field that carries the real one —
 * `RideView.cancellationReason` — was read by nobody because until B-58 nobody wrote it.
 *
 * The fallback stays, and it is the kit's R5·a headline: a server that says nothing is, in this
 * product, a cascade that ran out of drivers.
 */
internal fun MatchingUiState.headline(): String =
    when {
        stage == MatchingStage.LOOKING -> "looking for a car"
        else -> ride?.cancellationReason ?: "no cars nearby"
    }

/** `2nd`, `3rd`, `4th` — for "asking the 2nd closest", which is what a cascade past its first driver is doing. */
private fun Int.ordinal(): String =
    when {
        this % 100 in 11..13 -> "${this}th"
        this % 10 == 1 -> "${this}st"
        this % 10 == 2 -> "${this}nd"
        this % 10 == 3 -> "${this}rd"
        else -> "${this}th"
    }
