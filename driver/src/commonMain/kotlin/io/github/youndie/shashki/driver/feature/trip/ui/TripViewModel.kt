package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripProgression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

public data class DriverTripUiState(
    val ride: RideView? = null,
    val advancing: Boolean = false,
) {
    /** The one state the driver can move to from here, or `null` when the ride is over. */
    public val next: RideStatus? get() = ride?.status?.let(TripProgression::next)
}

public sealed interface DriverTripUiAction {
    /** The driver pressed the one button. Where it goes is [DriverTripUiState.next]. */
    public data object Advance : DriverTripUiAction
}

public sealed interface DriverTripUiEvent {
    /** The ride ended — cancelled by the rider, for now, since nothing else can end it. */
    public data object Finished : DriverTripUiEvent

    public data class Failed(
        val message: String,
    ) : DriverTripUiEvent
}

/** What the driver accepted, kept up to date. */
public class DriverTripViewModel(
    private val rideId: String,
    private val identity: DriverIdentity,
    private val observeTrip: ObserveTripUseCase,
    private val advanceTrip: AdvanceTripUseCase,
    loopScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(DriverTripUiState())
    public val uiState: StateFlow<DriverTripUiState> = _uiState.asStateFlow()

    private val _events = Channel<DriverTripUiEvent>(Channel.BUFFERED)
    public val events: Flow<DriverTripUiEvent> = _events.receiveAsFlow()

    init {
        scope.launch {
            observeTrip(rideId).collect { result ->
                result
                    .onSuccess { ride ->
                        _uiState.value = _uiState.value.copy(ride = ride, advancing = false)
                        if (ride.status in TERMINAL) _events.send(DriverTripUiEvent.Finished)
                    }.onFailure {
                        _events.send(DriverTripUiEvent.Failed(it.message ?: "the ride could not be read"))
                    }
            }
        }
    }

    public fun onAction(action: DriverTripUiAction) {
        when (action) {
            DriverTripUiAction.Advance -> advance()
        }
    }

    /**
     * **The state is taken from the poll's answer, not from the button's intention.**
     *
     * The server decides whether a transition is the next one, and the request that moves a trip to
     * `COMPLETED` is the one that captures the rider's money — so what the screen shows afterwards
     * has to be what came back. A screen that advanced its own state optimistically would show a
     * finished ride whenever the network hiccuped.
     */
    private fun advance() {
        val to = _uiState.value.next ?: return
        if (_uiState.value.advancing) return
        _uiState.value = _uiState.value.copy(advancing = true)
        scope.launch {
            advanceTrip(AdvanceTripUseCase.Params(rideId, identity.current(), to))
                .onSuccess { ride ->
                    _uiState.value = DriverTripUiState(ride)
                    if (ride.status in TERMINAL) _events.send(DriverTripUiEvent.Finished)
                }.onFailure {
                    _uiState.value = _uiState.value.copy(advancing = false)
                    _events.send(DriverTripUiEvent.Failed(it.message ?: "the server refused the transition"))
                }
        }
    }

    private companion object {
        val TERMINAL = setOf(RideStatus.COMPLETED, RideStatus.CANCELLED)
    }
}
