package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.DriverConfig
import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.feature.shift.domain.PositionFixes
import io.github.youndie.shashki.driver.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.ObserveTripUseCase
import io.github.youndie.shashki.driver.feature.trip.domain.TripRepository
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.LegTarget
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripProgression
import io.github.youndie.shashki.ui.map.CarMarker
import io.github.youndie.shashki.ui.map.MapCamera
import io.github.youndie.shashki.ui.map.MapPin
import io.github.youndie.shashki.ui.map.MapScene
import io.github.youndie.shashki.ui.map.RouteLine
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
    /** The road to the next point, the car and the two pins (B-75). Empty until the first read. */
    val scene: MapScene = MapScene(camera = MapCamera(DriverConfig.LJUBLJANA_CENTRE)),
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
    /** Where this driver is, for the car on the map and the start of the road to draw (B-75). */
    private val positions: PositionFixes,
    private val configured: GeoPoint,
    private val roads: TripRepository,
    loopScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(DriverTripUiState())
    public val uiState: StateFlow<DriverTripUiState> = _uiState.asStateFlow()

    private val _events = Channel<DriverTripUiEvent>(Channel.BUFFERED)
    public val events: Flow<DriverTripUiEvent> = _events.receiveAsFlow()

    /** Which leg the road on the map is for, so it is fetched once per leg and not per poll. */
    private var roadFor: LegTarget? = null

    init {
        scope.launch {
            observeTrip(rideId).collect { result ->
                result
                    .onSuccess { ride ->
                        _uiState.value = _uiState.value.copy(ride = ride, advancing = false, scene = sceneFor(ride))
                        if (ride.status in TERMINAL) _events.send(DriverTripUiEvent.Finished)
                    }.onFailure {
                        _events.send(DriverTripUiEvent.Failed(it.message ?: "the ride could not be read"))
                    }
            }
        }
        // **The car is this driver's own position**, from the same source the shift reports up the
        // socket — the device when it says, the configured point when it does not. The camera goes
        // with it: a driver looks at where they are, not at where the rider is.
        scope.launch {
            positions.fixes(configured).collect { fix ->
                val scene = _uiState.value.scene
                _uiState.value =
                    _uiState.value.copy(
                        scene =
                            scene.copy(
                                camera = MapCamera(fix.at),
                                cars = listOf(CarMarker(id = "me", at = fix.at, self = true)),
                            ),
                    )
            }
        }
    }

    /**
     * The pins and the road for the current leg (B-75).
     *
     * To the pickup until the car has arrived, from the pickup to the drop-off after; the server
     * routes it once per leg and the poll does not re-ask. The road's start is the configured point
     * rather than the live fix on purpose: a road re-fetched every fix is a search per second for a
     * line that hardly moves, and the car marker is what shows where the driver actually is.
     */
    private suspend fun sceneFor(ride: RideView): MapScene {
        val existing = _uiState.value.scene
        val target =
            when (ride.status) {
                RideStatus.ASSIGNED, RideStatus.ARRIVING -> LegTarget.PICKUP
                else -> LegTarget.DROPOFF
            }
        val pins = listOf(MapPin(ride.pickup, MapPin.Kind.PICKUP), MapPin(ride.dropoff, MapPin.Kind.DROPOFF))
        if (target == roadFor) return existing.copy(pins = pins)
        val (from, to) = if (target == LegTarget.PICKUP) configured to ride.pickup else ride.pickup to ride.dropoff
        val road = runCatching { roads.road(from, to) }.getOrNull()
        if (road != null) roadFor = target
        return existing.copy(
            route = road?.let { RouteLine(travelled = emptyList(), ahead = it) } ?: existing.route,
            pins = pins,
        )
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
