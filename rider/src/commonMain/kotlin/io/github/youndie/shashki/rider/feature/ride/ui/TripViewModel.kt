package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.rider.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ObserveRideUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.WatchDriverUseCase
import io.github.youndie.shashki.ui.map.CarMarker
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.RouteLine
import io.github.youndie.shashki.ui.screens.TripStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

public data class TripUiState(
    val ride: RideView? = null,
    val stage: TripStage = TripStage.ARRIVING,
    val scene: MapScene = MapScene(camera = MapCamera(LJUBLJANA)),
    val cancelling: Boolean = false,
    /** Whether R10 is up. The fee it shows is the ride's, never a rule repeated here (B-43). */
    val confirming: Boolean = false,
) {
    public companion object {
        public val LJUBLJANA: io.github.youndie.shashki.protocol.GeoPoint =
            io.github.youndie.shashki.protocol
                .GeoPoint(46.0511, 14.5051)
    }
}

public sealed interface TripUiAction {
    /** Asks: R10 goes up with the fee on it. [ConfirmCancel] is the one that calls the server. */
    public data object Cancel : TripUiAction

    public data object ConfirmCancel : TripUiAction

    public data object DismissConfirm : TripUiAction

    public data object Call : TripUiAction
}

public sealed interface TripUiEvent {
    public data class Failed(
        val message: String,
    ) : TripUiEvent

    public data object Finished : TripUiEvent
}

/**
 * The trip, as the rider sees it: a status that advances, a road, and a car on it.
 *
 * **Two loops, because the two facts move at different speeds and fail separately.** The ride's
 * status changes a handful of times in twenty minutes; the car moves every few seconds and its
 * position is the thing that goes missing in a tunnel. One loop asking for both would make a silent
 * phone look like a lost ride.
 */
public class TripViewModel(
    private val rideId: String,
    private val observeRide: ObserveRideUseCase,
    private val watchDriver: WatchDriverUseCase,
    private val cancelRide: CancelRideUseCase,
    /**
     * Where the two loops run. `null` means this view model's own scope, which is what an
     * application wants.
     *
     * **It is a parameter because the driver loop never ends on its own** — a car keeps moving for as
     * long as the screen is open, and the screen's lifetime is the loop's. That is right in an
     * application and unusable in a test: `runTest` drains its scheduler before finishing, and a
     * loop with no end drains for ever. A test passes its own `backgroundScope` and gets the
     * cancellation the screen would have given it.
     */
    loopScope: CoroutineScope? = null,
) : ViewModel() {
    // Named apart from the property because a constructor parameter shadows it inside `init`.
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(TripUiState())
    public val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripUiEvent>(Channel.BUFFERED)
    public val events: kotlinx.coroutines.flow.Flow<TripUiEvent> = _events.receiveAsFlow()

    init {
        scope.launch {
            observeRide(rideId).collect { result ->
                result
                    .onSuccess { ride ->
                        _uiState.value =
                            _uiState.value.copy(ride = ride, stage = ride.status.asStage(), scene = sceneFor(ride))
                        if (ride.status in ObserveRideUseCase.TERMINAL) _events.send(TripUiEvent.Finished)
                    }.onFailure { _events.send(TripUiEvent.Failed(it.message ?: "the ride could not be read")) }
            }
        }
        scope.launch {
            watchDriver(rideId).collect { driver ->
                // A driver with no position leaves the previous car where it was: the phone is quiet,
                // the car has not vanished. Removing the marker would be the screen lying about it.
                val at = driver.at ?: return@collect
                _uiState.value =
                    _uiState.value.copy(
                        scene =
                            _uiState.value.scene.copy(
                                cars =
                                    listOf(
                                        CarMarker(
                                            id = driver.driverId,
                                            at = at,
                                            bearingDegrees = driver.bearingDegrees,
                                        ),
                                    ),
                            ),
                    )
            }
        }
    }

    public fun onAction(action: TripUiAction) {
        when (action) {
            // Calling the driver is the kit's ring and nothing behind it: this product has no
            // telephony, and a button that dialled nothing would be worse than one that waits.
            TripUiAction.Call -> {}

            // **Asked before done, and with the number** (B-43). From this screen a driver has set
            // off, so cancelling settles a fee rather than rolling anything back — the one place in
            // this product where a tap moves money without a second sentence.
            TripUiAction.Cancel -> {
                _uiState.value = _uiState.value.copy(confirming = true)
            }

            TripUiAction.DismissConfirm -> {
                _uiState.value = _uiState.value.copy(confirming = false)
            }

            TripUiAction.ConfirmCancel -> {
                _uiState.value = _uiState.value.copy(confirming = false)
                cancel()
            }
        }
    }

    private fun cancel() {
        if (_uiState.value.cancelling) return
        scope.launch {
            _uiState.value = _uiState.value.copy(cancelling = true)
            cancelRide(rideId).onFailure { _events.send(TripUiEvent.Failed(it.message ?: "it could not be cancelled")) }
            _uiState.value = _uiState.value.copy(cancelling = false)
        }
    }

    /** The pins and the road; the car arrives on the other loop. */
    private suspend fun sceneFor(ride: RideView): MapScene {
        val existing = _uiState.value.scene
        if (existing.route != null) return existing.copy(camera = MapCamera(ride.pickup))
        val road = runCatching { watchDriver.roadFor(ride) }.getOrNull()
        return MapScene(
            camera = MapCamera(ride.pickup),
            route = road?.let { RouteLine(travelled = emptyList(), ahead = it) },
            cars = existing.cars,
            pins = listOf(MapPin(ride.pickup, MapPin.Kind.PICKUP), MapPin(ride.dropoff, MapPin.Kind.DROPOFF)),
        )
    }
}

/**
 * `RideStatus` to what the screen says.
 *
 * The three the screen has are the three the trip has; everything before `ARRIVING` is the order
 * still being placed, and the rider is on R4 for that.
 */
private fun RideStatus.asStage(): TripStage =
    when (this) {
        RideStatus.ARRIVED -> TripStage.ARRIVED
        RideStatus.IN_PROGRESS -> TripStage.IN_PROGRESS
        else -> TripStage.ARRIVING
    }
