package io.github.youndie.shashki.rider.feature.ride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.LegTarget
import io.github.youndie.shashki.protocol.LegView
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

public data class TripUiState(
    val ride: RideView? = null,
    val stage: TripStage = TripStage.ARRIVING,
    val scene: MapScene = MapScene(camera = MapCamera(LJUBLJANA)),
    val cancelling: Boolean = false,
    /** Whether R10 is up. The fee it shows is the ride's, never a rule repeated here (B-43). */
    val confirming: Boolean = false,
    /**
     * `20:06` — when the car arrives, as a clock (B-77).
     *
     * **Computed here, once per poll, from the leg the server sent and the client's own clock** —
     * which is the right clock for a wall-clock time: the rider's watch is the one they will compare
     * it with. `null` while the trip has no leg to the drop-off yet.
     */
    val arrivingAt: String? = null,
    /**
     * How long the car has been silent, in seconds, once that is long enough to say — the kit's
     * R7·a, *gps lost* (B-80). `null` while positions arrive, and for the first half minute they do
     * not: a phone in a tunnel for ten seconds is not a lost car.
     */
    val quietForSeconds: Int? = null,
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
    /** The rider's wall clock, for "arriving 20:06" (B-77). A parameter so a test can hold it still. */
    private val now: () -> Long,
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
                            _uiState.value.copy(
                                ride = ride,
                                stage = ride.status.asStage(),
                                scene = sceneFor(ride),
                                arrivingAt = ride.leg?.takeIf { it.to == LegTarget.DROPOFF }?.let { arrivingAt(it) },
                            )
                        if (ride.status in ObserveRideUseCase.TERMINAL) _events.send(TripUiEvent.Finished)
                    }.onFailure { _events.send(TripUiEvent.Failed(it.message ?: "the ride could not be read")) }
            }
        }
        scope.launch {
            watchDriver(rideId).collect { driver ->
                // A driver with no position leaves the previous car where it was: the phone is quiet,
                // the car has not vanished. Removing the marker would be the screen lying about it.
                // What the screen does say, after half a minute of it, is how long (B-80).
                val at = driver.at
                if (at == null) {
                    val quiet = ((now() - (lastFixAt ?: now().also { lastFixAt = it })) / MILLIS).toInt()
                    _uiState.value = _uiState.value.copy(quietForSeconds = quiet.takeIf { it >= GPS_LOST_SECONDS })
                    return@collect
                }
                lastFixAt = now()
                val scene = _uiState.value.scene
                _uiState.value =
                    _uiState.value.copy(
                        scene =
                            scene.copy(
                                cars =
                                    listOf(
                                        CarMarker(
                                            id = driver.driverId,
                                            at = at,
                                            bearingDegrees = driver.bearingDegrees,
                                        ),
                                    ),
                                // **The road behind the car goes to 25 % white and the road ahead
                                // stays the accent** (B-77) — "progress is colour, not thickness".
                                // The split is at the vertex nearest the car, and only once the
                                // trip is running: before pickup the car is not on this road at all.
                                route =
                                    if (_uiState.value.stage == TripStage.IN_PROGRESS) {
                                        road?.let { splitAt(it, at) } ?: scene.route
                                    } else {
                                        scene.route
                                    },
                            ),
                        quietForSeconds = null,
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

    /** The whole road, pickup to drop-off, kept so the car can split it as it moves (B-77). */
    private var road: List<GeoPoint>? = null

    /** When the car last said where it was, on this clock — or when it first went quiet (B-80). */
    private var lastFixAt: Long? = null

    /** The pins and the road; the car arrives on the other loop. */
    private suspend fun sceneFor(ride: RideView): MapScene {
        val existing = _uiState.value.scene
        if (existing.route != null) return existing.copy(camera = MapCamera(ride.pickup))
        val fetched = runCatching { watchDriver.roadFor(ride) }.getOrNull()
        road = fetched
        return MapScene(
            camera = MapCamera(ride.pickup),
            route = fetched?.let { RouteLine(travelled = emptyList(), ahead = it) },
            cars = existing.cars,
            pins = listOf(MapPin(ride.pickup, MapPin.Kind.PICKUP), MapPin(ride.dropoff, MapPin.Kind.DROPOFF)),
        )
    }

    /** `HH:MM` on the rider's clock, [leg]'s seconds from now. */
    private fun arrivingAt(leg: LegView): String {
        val at =
            Instant
                .fromEpochMilliseconds(
                    now() + leg.durationSeconds * MILLIS,
                ).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    }

    private companion object {
        const val MILLIS = 1_000L

        /** The kit's band says "last position 40 seconds ago"; this is when the band goes up. */
        const val GPS_LOST_SECONDS = 30
    }
}

/**
 * The road in its two phases, split where the car is (B-77).
 *
 * **Nearest vertex, not nearest point on a segment.** The road comes from the router at a few metres
 * between vertices, and the car's position from a phone's GPS at a few metres of error; a projection
 * onto the segment would be precision the inputs do not have. The car itself is the joint, so the
 * two phases meet under the marker rather than a vertex away from it.
 */
internal fun splitAt(
    road: List<GeoPoint>,
    car: GeoPoint,
): RouteLine {
    if (road.isEmpty()) return RouteLine(travelled = emptyList(), ahead = emptyList())
    val nearest = road.indices.minBy { distanceSquared(road[it], car) }
    return RouteLine(
        travelled = road.subList(0, nearest + 1) + car,
        ahead =
            listOf(car) + road.subList(nearest, road.size),
    )
}

/** Flat-earth squared distance in degrees — enough to pick a vertex, and cheap enough to do per fix. */
private fun distanceSquared(
    a: GeoPoint,
    b: GeoPoint,
): Double {
    val dLat = a.lat - b.lat
    val dLon = (a.lon - b.lon) * kotlin.math.cos(a.lat * kotlin.math.PI / 180.0)
    return dLat * dLat + dLon * dLon
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
