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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Everything R4 shows, as one value. */
public data class ClassPickerUiState(
    val destination: String = "airport",
    val loading: Boolean = true,
    val quotes: List<ClassQuote> = emptyList(),
    val selected: RideClass = RideClass.ECONOMY,
    val distanceMetres: Int = 0,
    val durationSeconds: Int = 0,
    val ordering: Boolean = false,
) {
    /** Whether a car of this class is near enough for the server to have named a wait. */
    public fun hasCars(rideClass: RideClass): Boolean =
        quotes.any { it.rideClass == rideClass && it.pickupEtaSeconds != null }
}

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
            // **A class nobody is driving cannot be selected.** The kit's tile draws it in its
            // unavailable state and still reports a click — that is the component being a component
            // — so refusing is this screen's job. Ordering one would be a ride the saga cancels for
            // want of cars a second later, which is a worse answer than the tile already gave.
            is ClassPickerUiAction.Select -> {
                if (_uiState.value.hasCars(action.rideClass)) {
                    _uiState.value = _uiState.value.copy(selected = action.rideClass)
                }
            }

            ClassPickerUiAction.Retry -> {
                load()
            }

            ClassPickerUiAction.Order -> {
                order()
            }
        }
    }

    /**
     * Ask again, for as long as the screen is on top (B-66).
     *
     * **The price does not go stale and the wait does.** They arrive in one answer, which is what
     * made it easy to cache both by accident: R4 asked once, at startup, so a rider who opened the
     * application before anybody was driving read *no cars nearby* for ever — measured on the
     * desktop client, zero `POST /api/quotes` in the minute after the first load, and a restart the
     * only cure.
     *
     * **Called from the screen rather than started here**, which is what makes the second half of it
     * true: the loop lives in the composition's scope, so a class picker underneath a trip is asking
     * nobody anything. A view model that polled from `viewModelScope` would keep asking for as long
     * as the entry was on the back stack.
     *
     * Five seconds: `POST /api/quotes` answers in about 10 ms on the stand — a graph search for the
     * journey and one per class that has a candidate — and the thing being watched is a car arriving
     * within a few hundred metres. Slower than the driver's board at two seconds, faster than a
     * rider's patience.
     */
    public suspend fun watch() {
        while (true) {
            delay(QUOTE_INTERVAL)
            refresh()
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
                                // **The selection follows the cars.**
                                selected = quotes.classes.selectable(_uiState.value.selected),
                            )
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(loading = false)
                        _events.send(ClassPickerUiEvent.Failed(it.message ?: "the server did not answer"))
                    }
            }
    }

    /**
     * One more ask, on the way round the loop.
     *
     * **A failed refresh keeps what is on the screen and says nothing.** The first load is different
     * — a screen with no prices has to report why — but a rider watching three tiles does not need a
     * banner every five seconds about a poll that will be repeated in five more, and B-64's own
     * lesson is that the two cases are different rather than that failures should be silent.
     */
    private suspend fun refresh() {
        quoteJourney(QuoteJourneyUseCase.Params(pickup, dropoff))
            .onSuccess { quotes ->
                _uiState.value =
                    _uiState.value.copy(
                        loading = false,
                        quotes = quotes.classes,
                        distanceMetres = quotes.distanceMetres,
                        durationSeconds = quotes.durationSeconds,
                        selected = quotes.classes.selectable(_uiState.value.selected),
                    )
            }
    }

    /**
     * The class to open on: the current one if it has cars, otherwise the first that does.
     *
     * `ECONOMY` is the default before anything is known. If it turns out nobody is driving one, the
     * screen would otherwise open on a greyed row with the order bar live under it — which is a
     * screen inviting an action it has already said is unavailable. Falling back to nothing (all
     * three empty) keeps the current selection, because there is nothing better to move to.
     */
    private fun List<ClassQuote>.selectable(current: RideClass): RideClass =
        if (any { it.rideClass == current && it.pickupEtaSeconds != null }) {
            current
        } else {
            firstOrNull { it.pickupEtaSeconds != null }?.rideClass ?: current
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

/** How often R4 asks again while it is on top (B-66). */
private val QUOTE_INTERVAL = 5.seconds
