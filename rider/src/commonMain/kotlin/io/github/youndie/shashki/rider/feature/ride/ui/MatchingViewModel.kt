package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.ui.screens.MatchingStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** R5 and R5·a, plus whether R10 is over them. */
public data class MatchingUiState(
    val stage: MatchingStage = MatchingStage.LOOKING,
    val ride: RideView? = null,
    /** Whether the confirmation is up. The fee it shows comes from [ride], never from a rule here. */
    val confirming: Boolean = false,
    val cancelling: Boolean = false,
)

public sealed interface MatchingUiAction {
    /** The bar: *cancel* while looking, *try again* once the cars have run out. */
    public data object Act : MatchingUiAction

    public data object ConfirmCancel : MatchingUiAction

    public data object DismissConfirm : MatchingUiAction
}

public sealed interface MatchingUiEvent {
    /** A driver took it. The screen hands the ride over to the trip. */
    public data class Assigned(
        val rideId: String,
    ) : MatchingUiEvent

    /** Back to the picker — either the rider cancelled, or they asked to try again. */
    public data object Back : MatchingUiEvent

    public data class Failed(
        val message: String,
    ) : MatchingUiEvent
}

/**
 * The wait, and the three ways out of it: a driver, no drivers, or the rider changing their mind.
 *
 * **The stage is read off the ride and not tracked here.** `MATCHING` is the saga still asking;
 * anything from `ASSIGNED` on is a car; `CANCELLED` is either the cascade running out of drivers or
 * this screen having cancelled it a moment ago — and the screen is the one thing that knows which,
 * because it did it. That is the whole of [cancelling]: the server's `CANCELLED` is one status for
 * two events, and only the client can tell them apart without a reason field the saga does not
 * currently record.
 *
 * The loop is `ObserveRideUseCase`'s, the same one the trip screen uses, and it stops itself at a
 * terminal status.
 */
public class MatchingViewModel(
    private val rideId: String,
    observeRide: ObserveRideUseCase,
    private val cancelRide: CancelRideUseCase,
    /**
     * Where the poll runs. `null` means this view model's own scope, which is what an application
     * wants — and `TripViewModel` carries the same parameter for the same reason.
     *
     * **A ride that nobody takes is polled for ever**, which is right on a screen and unusable in a
     * test: `runTest` drains its scheduler before finishing, and a loop with no end drains for ever.
     * A test hands in its own `backgroundScope` and gets the cancellation the screen would have
     * given it. (Measured rather than foreseen: the first version of the test hung for ten minutes.)
     */
    loopScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(MatchingUiState())
    public val uiState: StateFlow<MatchingUiState> = _uiState.asStateFlow()

    private val _events = Channel<MatchingUiEvent>(Channel.BUFFERED)
    public val events: Flow<MatchingUiEvent> = _events.receiveAsFlow()

    init {
        scope.launch {
            observeRide(rideId).collect { result ->
                result
                    .onSuccess { ride ->
                        _uiState.value = _uiState.value.copy(ride = ride)
                        // No `else`: still `MATCHING` means the search is still running, which is
                        // the state already on the screen.
                        if (ride.status in ASSIGNED_ON) {
                            _events.send(MatchingUiEvent.Assigned(rideId))
                        } else if (ride.status == RideStatus.CANCELLED && !_uiState.value.cancelling) {
                            _uiState.value = _uiState.value.copy(stage = MatchingStage.NO_CARS)
                        }
                    }.onFailure { _events.send(MatchingUiEvent.Failed(it.message ?: "the ride could not be read")) }
            }
        }
    }

    public fun onAction(action: MatchingUiAction) {
        when (action) {
            MatchingUiAction.Act -> {
                if (_uiState.value.stage == MatchingStage.NO_CARS) {
                    // *try again*: the picker is still underneath with the address and the class it
                    // had, so going back is the whole action. Ordering again from here would ask for
                    // the same class that has just run out of cars.
                    viewModelScope.launch { _events.send(MatchingUiEvent.Back) }
                } else {
                    _uiState.value = _uiState.value.copy(confirming = true)
                }
            }

            MatchingUiAction.DismissConfirm -> {
                _uiState.value = _uiState.value.copy(confirming = false)
            }

            MatchingUiAction.ConfirmCancel -> {
                cancel()
            }
        }
    }

    private fun cancel() {
        if (_uiState.value.cancelling) return
        // **Set before the call and never lowered.** It is what tells the poll below that the
        // `CANCELLED` about to arrive is this rider's doing rather than an empty city.
        _uiState.value = _uiState.value.copy(cancelling = true, confirming = false)
        viewModelScope.launch {
            cancelRide(rideId)
                .onSuccess { _events.send(MatchingUiEvent.Back) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(cancelling = false)
                    _events.send(MatchingUiEvent.Failed(it.message ?: "the ride could not be cancelled"))
                }
        }
    }

    private companion object {
        /** Everything past the search. The trip screen owns all of them. */
        val ASSIGNED_ON =
            setOf(
                RideStatus.ASSIGNED,
                RideStatus.ARRIVING,
                RideStatus.ARRIVED,
                RideStatus.IN_PROGRESS,
            )
    }
}
