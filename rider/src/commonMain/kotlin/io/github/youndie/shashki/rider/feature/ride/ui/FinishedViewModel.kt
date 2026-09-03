package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.FareBreakdown
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.domain.RateRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ReadRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.TipRideUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** R8, as the screen holds it: what it cost, the stars so far, and which tip is chosen. */
public data class FinishedUiState(
    val ride: RideView? = null,
    val stars: Int = 0,
    /** The index into the offered tips, or `null` when nothing has been chosen yet. */
    val selectedTip: Int? = null,
    /**
     * Whether *skip* is what this rider chose (B-59).
     *
     * **`selectedTip == null` used to mean skip**, so the screen opened with the refusal filled in
     * the accent — the one accent surface the kit allows a screen, spent recommending that nothing
     * be paid. Nothing is chosen when the screen opens, and skipping is a thing somebody does.
     */
    val skipped: Boolean = false,
    val sending: Boolean = false,
) {
    /** The tips this screen offers, in cents. Fixed amounts, because a percentage of what is a rule. */
    public companion object {
        public val TIPS: List<Long> = listOf(200, 500, 1_000)
    }
}

public sealed interface FinishedUiAction {
    public data class Stars(
        val stars: Int,
    ) : FinishedUiAction

    public data class Tip(
        val index: Int?,
    ) : FinishedUiAction

    public data object Done : FinishedUiAction
}

public sealed interface FinishedUiEvent {
    public data object Done : FinishedUiEvent

    public data class Failed(
        val message: String,
    ) : FinishedUiEvent
}

/**
 * The end of a ride (B-44).
 *
 * **Nothing is sent until *done*, and that is the whole shape of the screen.** Tapping a third star
 * and then a fourth would otherwise be two ratings, and the second would collide with the first — a
 * rider rates a ride once, which is the table's primary key. So the taps move state and the button
 * sends: at most one rating and at most one charge.
 *
 * **A rating with no tip is the ordinary case** and costs one call; *skip* costs none. What must not
 * happen is a tip going out because somebody looked at the row.
 */
public class FinishedViewModel(
    private val rideId: String,
    private val readRide: ReadRideUseCase,
    private val rateRide: RateRideUseCase,
    private val tipRide: TipRideUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FinishedUiState())
    public val uiState: StateFlow<FinishedUiState> = _uiState.asStateFlow()

    private val _events = Channel<FinishedUiEvent>(Channel.BUFFERED)
    public val events: Flow<FinishedUiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            readRide(rideId)
                // **The stars this ride already carries** (B-59). A screen that drew five empty
                // ones over a rated ride invited a second rating of the same journey, and the server
                // took it.
                .onSuccess { _uiState.value = _uiState.value.copy(ride = it, stars = it.stars ?: 0) }
                .onFailure { _events.send(FinishedUiEvent.Failed(it.message ?: "the ride could not be read")) }
        }
    }

    public fun onAction(action: FinishedUiAction) {
        when (action) {
            is FinishedUiAction.Stars -> {
                _uiState.value = _uiState.value.copy(stars = action.stars)
            }

            is FinishedUiAction.Tip -> {
                _uiState.value = _uiState.value.copy(selectedTip = action.index, skipped = action.index == null)
            }

            FinishedUiAction.Done -> {
                send()
            }
        }
    }

    private fun send() {
        val state = _uiState.value
        if (state.sending) return
        _uiState.value = state.copy(sending = true)
        viewModelScope.launch {
            try {
                // **The rating first and the money second.** If the charge fails the rating still
                // stands, which is the order a rider would want; the other way round, a refused
                // rating would swallow a tip they had already agreed to.
                // Only a rating this screen collected: re-sending the one that was read back would
                // be a second rating of the same ride, written by nobody's tap.
                if (state.stars > 0 && state.stars != state.ride?.stars) {
                    rateRide(RateRideUseCase.Params(rideId, state.stars)).getOrThrow()
                }
                state.selectedTip?.let { index ->
                    tipRide(TipRideUseCase.Params(rideId, FinishedUiState.TIPS[index])).getOrThrow()
                }
                _events.send(FinishedUiEvent.Done)
            } catch (e: IllegalStateException) {
                _uiState.value = _uiState.value.copy(sending = false)
                _events.send(FinishedUiEvent.Failed(e.message ?: "that did not go through"))
            }
        }
    }
}

/**
 * The fare plus the chosen tip, or `null` while nothing is chosen (B-59).
 *
 * **Adding two numbers this screen already holds is not pricing.** `FareBreakdown`'s rule — the
 * server sends lines and the client never computes a total — is about a receipt, whose lines are the
 * settlement's. Here the fare is what was charged and the tip is the chip a rider just pressed, and
 * a screen that showed neither sum would be asking somebody to do the arithmetic themselves.
 */
internal fun FinishedUiState.totalWithTipCents(): Long? {
    val charged = ride?.chargedCents ?: return null
    val tip = selectedTip?.let { FinishedUiState.TIPS.getOrNull(it) } ?: return null
    return charged + tip
}
