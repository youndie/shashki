package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.rider.feature.ride.domain.QuoteJourneyUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RequestRideUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Everything R4 shows, as one value. */
public data class ClassPickerUiState(
    val destination: String = "airport",
    val loading: Boolean = true,
    val quotes: List<ClassQuote> = emptyList(),
    val selected: RideClass = RideClass.ECONOMY,
    val distanceMetres: Int = 0,
    val durationSeconds: Int = 0,
    val ordering: Boolean = false,
)

public sealed interface ClassPickerUiAction {
    public data class Select(
        val rideClass: RideClass,
    ) : ClassPickerUiAction

    public data object Order : ClassPickerUiAction

    public data object Retry : ClassPickerUiAction
}

/** Once, not replayed: a snackbar that reappeared on rotation would be a second failure. */
public sealed interface ClassPickerUiEvent {
    public data class Failed(
        val message: String,
    ) : ClassPickerUiEvent

    public data class Ordered(
        val rideId: String,
    ) : ClassPickerUiEvent
}

/**
 * R4's state, and the only place in this screen that knows a server exists.
 *
 * **Use cases, never the repository.** The rule is the project's and it earns itself here: `Order`
 * is two operations — quote then request — and a view model reaching into a repository would have
 * spread the ordering of them across the UI layer.
 */
public class ClassPickerViewModel(
    private val quoteJourney: QuoteJourneyUseCase,
    private val requestRide: RequestRideUseCase,
    private val pickup: GeoPoint,
    private val dropoff: GeoPoint,
    private val riderId: String,
    private val paymentMethodId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClassPickerUiState())
    public val uiState: StateFlow<ClassPickerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ClassPickerUiEvent>(Channel.BUFFERED)
    public val events: kotlinx.coroutines.flow.Flow<ClassPickerUiEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    public fun onAction(action: ClassPickerUiAction) {
        when (action) {
            is ClassPickerUiAction.Select -> _uiState.value = _uiState.value.copy(selected = action.rideClass)
            ClassPickerUiAction.Retry -> load()
            ClassPickerUiAction.Order -> order()
        }
    }

    private fun load() {
        // The previous load is cancelled rather than raced: a stale answer arriving second would
        // overwrite a fresh one, and the screen would show prices for a journey nobody asked about.
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(loading = true)
                quoteJourney(QuoteJourneyUseCase.Params(pickup, dropoff))
                    .onSuccess { quotes ->
                        _uiState.value =
                            _uiState.value.copy(
                                loading = false,
                                quotes = quotes.classes,
                                distanceMetres = quotes.distanceMetres,
                                durationSeconds = quotes.durationSeconds,
                            )
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(loading = false)
                        _events.send(ClassPickerUiEvent.Failed(it.message ?: "the server did not answer"))
                    }
            }
    }

    private fun order() {
        if (_uiState.value.ordering) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(ordering = true)
            requestRide(
                RequestRideUseCase.Params(
                    riderId = riderId,
                    from = pickup,
                    to = dropoff,
                    rideClass = _uiState.value.selected,
                    paymentMethodId = paymentMethodId,
                ),
            ).onSuccess { _events.send(ClassPickerUiEvent.Ordered(it.id)) }
                .onFailure { _events.send(ClassPickerUiEvent.Failed(it.message ?: "the order was refused")) }
            _uiState.value = _uiState.value.copy(ordering = false)
        }
    }
}
