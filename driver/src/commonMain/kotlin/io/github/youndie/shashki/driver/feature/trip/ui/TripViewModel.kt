package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
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
)

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
    private val observeTrip: ObserveTripUseCase,
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
                        _uiState.value = DriverTripUiState(ride)
                        if (ride.status in TERMINAL) _events.send(DriverTripUiEvent.Finished)
                    }.onFailure {
                        _events.send(DriverTripUiEvent.Failed(it.message ?: "the ride could not be read"))
                    }
            }
        }
    }

    private companion object {
        val TERMINAL = setOf(RideStatus.COMPLETED, RideStatus.CANCELLED)
    }
}
