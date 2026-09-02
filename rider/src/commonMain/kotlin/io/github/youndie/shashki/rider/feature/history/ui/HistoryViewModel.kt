package io.github.youndie.shashki.rider.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.domain.MyRidesUseCase
import io.github.youndie.shashki.ui.format.asDistance
import io.github.youndie.shashki.ui.format.money
import io.github.youndie.shashki.ui.kompot.TripRow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** R9 as the screen holds it: the rows, and who the rider is. */
public data class HistoryUiState(
    val loading: Boolean = true,
    val trips: List<TripRow> = emptyList(),
    val profile: List<Pair<String, String>> = emptyList(),
)

public sealed interface HistoryUiEvent {
    public data class Failed(
        val message: String,
    ) : HistoryUiEvent
}

/**
 * The rider's own rides (B-45).
 *
 * **The rows are `TripRow`s — kompot's component, built natively.** The screen draws them with
 * kompot's own renderer, so a list this application assembles and one a server sends look the same
 * by construction rather than by inspection.
 *
 * **The fare on a row is what was taken, not what was quoted**, which is why a cancelled ride shows
 * a quarter of its journey and one nobody drove shows a dash: the two cancellations are told apart
 * in the list exactly as they are in the settlement.
 */
public class HistoryViewModel(
    private val myRides: MyRidesUseCase,
    profile: List<Pair<String, String>>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState(profile = profile))
    public val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = Channel<HistoryUiEvent>(Channel.BUFFERED)
    public val events: Flow<HistoryUiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            myRides(Unit)
                .onSuccess { rides ->
                    _uiState.value = _uiState.value.copy(loading = false, trips = rides.map { it.asRow() })
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loading = false)
                    _events.send(HistoryUiEvent.Failed(it.message ?: "the trips could not be read"))
                }
        }
    }
}

/**
 * One ride, as a row.
 *
 * The accent goes to the newest — the kit allows one surface per screen, and the list's first row is
 * where a rider looks. `—` is a ride that cost nothing: the cascade ran out of cars, nobody drove,
 * and no money moved.
 */
internal fun RideView.asRow(): TripRow =
    TripRow(
        id = id,
        title = "airport",
        meta =
            listOfNotNull(
                when (status) {
                    RideStatus.CANCELLED -> "cancelled"
                    RideStatus.COMPLETED -> "completed"
                    else -> "in progress"
                },
                quote?.distanceMetres?.asDistance(),
            ).joinToString(" · "),
        amount = chargedCents?.let { money(it, quote?.currency ?: "USD") } ?: "—",
    )
