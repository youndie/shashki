package io.github.youndie.shashki.driver.feature.shift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.AnswerOutcome
import io.github.youndie.shashki.driver.feature.offer.domain.WatchOfferUseCase
import io.github.youndie.shashki.driver.feature.offer.domain.remainingAtReceipt
import io.github.youndie.shashki.driver.feature.shift.domain.GoOnlineUseCase
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

public data class ShiftUiState(
    /** Who this bundle is signed in as. Config, but the screen shows it, so it is state. */
    val driverLabel: String = "",
    val online: Boolean = false,
    /** Reports the socket has taken. A shift with a rising count is a socket that is actually up. */
    val reported: Int = 0,
    val offer: OfferView? = null,
    val secondsLeft: Int = 0,
    val secondsTotal: Int = 0,
    val answering: Boolean = false,
)

public sealed interface ShiftUiAction {
    public data object ToggleOnline : ShiftUiAction

    public data object Accept : ShiftUiAction

    public data object Decline : ShiftUiAction
}

public sealed interface ShiftUiEvent {
    public data class Accepted(
        val rideId: String,
    ) : ShiftUiEvent

    /** The offer went to somebody else while this screen still had it. */
    public data object Gone : ShiftUiEvent

    public data class Failed(
        val message: String,
    ) : ShiftUiEvent
}

/**
 * The shift: a socket that stays up, a board that is polled, and fifteen seconds to decide.
 *
 * **Three loops, and they are three because they end at different times.** The socket lives for as
 * long as the driver is online; the poll lives with it; the countdown lives for one offer. Folding
 * them together would tie an offer's expiry to a reconnect.
 *
 * **The countdown counts a duration the server handed over.** `expiresAtEpochMs - nowEpochMs` is
 * measured on one clock — the server's — and this ticks it down with `delay`, so a device whose wall
 * clock is wrong draws the right number of seconds. What it deliberately does *not* do is decide
 * anything: reaching zero drops the card, and whether an answer was in time is settled where the
 * saga is, which answers 409 when it was not.
 */
public class ShiftViewModel(
    private val driverId: String,
    private val rideClass: RideClass,
    private val rating: Double,
    private val at: GeoPoint,
    private val goOnline: GoOnlineUseCase,
    private val watchOffer: WatchOfferUseCase,
    private val answerOffer: AnswerOfferUseCase,
    /** Where the loops run; `null` is this view model's own scope. See `TripViewModel` for why. */
    loopScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(ShiftUiState(driverLabel = driverId))
    public val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    private val _events = Channel<ShiftUiEvent>(Channel.BUFFERED)
    public val events: Flow<ShiftUiEvent> = _events.receiveAsFlow()

    private var shift: Job? = null
    private var poll: Job? = null
    private var countdown: Job? = null

    /**
     * The offer this screen has finished with — answered, or run out of seconds.
     *
     * **The board does not go empty the instant the card does.** A decline withdraws the offer
     * server-side, but the poll that is already in flight can still carry it, and an expiry the
     * client counted happens a moment before the one the server counts. Without this the card comes
     * straight back: answered, then offered again, then answered again. Cleared when the board
     * actually goes empty, which is the server agreeing.
     */
    private var finished: String? = null

    public fun onAction(action: ShiftUiAction) {
        when (action) {
            ShiftUiAction.ToggleOnline -> if (_uiState.value.online) goOffline() else online()
            ShiftUiAction.Accept -> answer(DriverDecision.ACCEPT)
            ShiftUiAction.Decline -> answer(DriverDecision.DECLINE)
        }
    }

    private fun online() {
        if (shift != null) return
        _uiState.value = _uiState.value.copy(online = true, reported = 0)
        shift =
            scope.launch {
                runCatching {
                    goOnline(driverId, rideClass, rating, at).collect {
                        _uiState.value = _uiState.value.copy(reported = _uiState.value.reported + 1)
                    }
                }.onFailure {
                    // A socket that will not open is the whole feature failing, and silently looking
                    // online for an hour is the failure mode this exists to avoid.
                    _events.send(ShiftUiEvent.Failed(it.message ?: "the position socket closed"))
                }
                goOffline()
            }
        poll =
            scope.launch {
                watchOffer(driverId).collect { offer -> onOffer(offer) }
            }
    }

    private fun goOffline() {
        shift?.cancel()
        shift = null
        poll?.cancel()
        poll = null
        countdown?.cancel()
        countdown = null
        finished = null
        _uiState.value = ShiftUiState(driverLabel = driverId)
    }

    private fun onOffer(offer: OfferView?) {
        val current = _uiState.value.offer
        if (offer == null) {
            // The board going empty while a card is up means the offer expired or was withdrawn
            // server-side. The card goes with it — leaving it would invite an accept that 409s.
            if (current != null) clearOffer()
            finished = null
            return
        }
        if (offer.rideId == finished || current?.rideId == offer.rideId) return
        val seconds =
            offer
                .remainingAtReceipt()
                .inWholeSeconds
                .toInt()
                .coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(offer = offer, secondsLeft = seconds, secondsTotal = seconds)
        countdown?.cancel()
        countdown =
            scope.launch {
                var left = seconds
                while (left > 0) {
                    delay(1.seconds)
                    left -= 1
                    _uiState.value = _uiState.value.copy(secondsLeft = left)
                }
                clearOffer()
            }
    }

    private fun clearOffer() {
        finished = _uiState.value.offer?.rideId ?: finished
        countdown?.cancel()
        countdown = null
        _uiState.value = _uiState.value.copy(offer = null, secondsLeft = 0, secondsTotal = 0, answering = false)
    }

    private fun answer(decision: DriverDecision) {
        val offer = _uiState.value.offer ?: return
        if (_uiState.value.answering) return
        _uiState.value = _uiState.value.copy(answering = true)
        scope.launch {
            answerOffer(AnswerOfferUseCase.Params(offer.rideId, driverId, decision))
                .onSuccess { outcome ->
                    when (outcome) {
                        is AnswerOutcome.Accepted -> {
                            clearOffer()
                            _events.send(ShiftUiEvent.Accepted(outcome.ride.id))
                        }

                        is AnswerOutcome.Gone -> {
                            clearOffer()
                            _events.send(ShiftUiEvent.Gone)
                        }

                        AnswerOutcome.Declined -> {
                            clearOffer()
                        }
                    }
                }.onFailure {
                    _uiState.value = _uiState.value.copy(answering = false)
                    _events.send(ShiftUiEvent.Failed(it.message ?: "the answer did not reach the server"))
                }
        }
    }
}
