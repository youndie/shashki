package io.github.youndie.shashki.driver.feature.shift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.driver.feature.earnings.ui.trips
import io.github.youndie.shashki.driver.feature.shift.domain.PositionSource
import io.github.youndie.shashki.protocol.EarningsTile
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.format.asCoordinates
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.asMoney
import io.github.youndie.shashki.protocol.format.money
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
    /** The header opens D6 — where a driver looks between rides (B-46). */
    onEarnings: () -> Unit = {},
    /** And the line under it opens D1 (B-47). */
    onDocuments: () -> Unit = {},
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

    ShiftContent(uiState, viewModel::onAction, modifier, onEarnings, onDocuments)
}

/** Stateless: `uiState` and `onAction`. No graph, no socket, no server. */
@Composable
public fun ShiftContent(
    uiState: ShiftUiState,
    onAction: (ShiftUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onEarnings: () -> Unit = {},
    onDocuments: () -> Unit = {},
) {
    DriverShift(
        state =
            DriverShiftState(
                online = uiState.online,
                tiles = uiState.tiles(),
                driverLabel = uiState.driverLabel,
                classLabel =
                    uiState.offer
                        ?.rideClass
                        ?.name
                        ?.lowercase() ?: "on shift",
                reported = uiState.reported.takeIf { uiState.online },
                // **The word for it, chosen here.** The view model holds an enum and the screen
                // draws a string; "configured" is the one a driver has to be able to read as "this
                // is where I said I am", not as an error (B-49).
                // The board's own trouble, in a driver's words: what they can do about it is
                // nothing, and what it explains is why the shift is quiet (B-64).
                boardLabel = uiState.boardUnreachable?.let { "offers unavailable" },
                positionLabel =
                    when {
                        !uiState.online -> null
                        uiState.positionSource == PositionSource.DEVICE -> "position: device"
                        else -> "position: configured"
                    },
                offer = uiState.offer?.asOfferState(uiState.secondsLeft, uiState.secondsTotal),
            ),
        onToggleOnline = { onAction(ShiftUiAction.ToggleOnline) },
        onAccept = { onAction(ShiftUiAction.Accept) },
        onDecline = { onAction(ShiftUiAction.Decline) },
        modifier = modifier,
        onEarnings = onEarnings,
        onDocuments = onDocuments,
        // **The word, not the state.** This screen does not know what is missing — the states come
        // from the store and are read on D1 itself; a label that guessed here would be a second
        // answer to the same question (B-47).
        documentsLabel = "documents",
    )
}

/**
 * The offer, as words.
 *
 * **The pickup's meta used to be a dash, and the dash was honest until B-74.** The kit draws
 * `2.1 km · 4 min from you` there — how far the *driver* is from the pickup — and the server
 * answered no such question; the quote it sent was the rider's journey, and a number borrowed from
 * the wrong leg would have read as an answer. The server routes the driver's own road now, from the
 * position their socket reported, and the dash is what remains for a driver it has no position for.
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
        // `2.1 km · 4 min from you` — the road from where this driver is, routed on the server
        // (B-74); a dash when it has no position for them or no road, rather than a guess.
        pickupMeta =
            fromDriverMetres?.let { metres ->
                fromDriverSeconds?.let { seconds -> "${metres.asDistance()} · ${seconds.asDuration()} from you" }
            } ?: "—",
        dropoff = dropoff.asCoordinates(),
        dropoffMeta = "${quote.distanceMetres.asDistance()} · ${quote.durationSeconds.asDuration()}",
    )

/**
 * The kit's D2 tiles (B-81): hours online carries the accent — the number that matters on a shift —
 * today's takings with the count beside them, and the rating. Acceptance is not here: nothing on
 * the server counts offers answered against offers made, and a tile with a number nobody measured
 * would be a decoration.
 */
internal fun ShiftUiState.tiles(): List<EarningsTile> {
    val online = onlineForSeconds ?: return emptyList()
    val earned = earnings
    return listOfNotNull(
        EarningsTile(
            "online",
            "hours online",
            "${online / 3600}:${(online % 3600 / 60).toString().padStart(2, '0')}",
            size = 2,
            accent = true,
        ),
        earned?.let {
            EarningsTile(
                "today",
                "today · ${it.todayTrips.trips()}",
                money(it.todayCents, it.currency),
                size = 2,
            )
        },
        earned?.rating?.let { EarningsTile("rating", "rating", "${(it * 10).toInt() / 10.0}", size = 2) },
    )
}
