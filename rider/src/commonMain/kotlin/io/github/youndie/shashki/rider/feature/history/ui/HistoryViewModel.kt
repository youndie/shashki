package io.github.youndie.shashki.rider.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.TripRow
import io.github.youndie.shashki.protocol.format.asCoordinates
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.money
import io.github.youndie.shashki.rider.feature.ride.domain.MyRidesUseCase
import io.github.youndie.shashki.ui.screens.TripMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** R9 as the screen holds it: the rows, and who the rider is. */
public data class HistoryUiState(
    val loading: Boolean = true,
    /**
     * The rider's rides, grouped by month and newest first (B-61).
     *
     * **Grouping is the screen's shape rather than a decoration.** A flat list of destinations reads
     * as nothing when every ride goes to the same place; a list read by when things happened is R9.
     */
    val months: List<TripMonth> = emptyList(),
    /**
     * The rides that are over, and therefore the rows that open a receipt (B-61).
     *
     * **A set of ids rather than a flag on the row**, because a row is a `TripRow` — a wire
     * component, which a server may also send — and a field saying where this client should navigate
     * would be a routing decision inside a payload. What is over is something this half already
     * knows: it read the statuses to write the meta line.
     */
    val settled: Set<String> = emptySet(),
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
                    // The server answers newest first, and `groupBy` keeps that order inside each
                    // month and between them.
                    _uiState.value =
                        _uiState.value.copy(
                            loading = false,
                            settled =
                                rides
                                    .filter { it.status == RideStatus.COMPLETED || it.status == RideStatus.CANCELLED }
                                    .mapTo(mutableSetOf()) { it.id },
                            months =
                                rides.groupBy { ride -> ride.month() }.map { (title, rides) ->
                                    TripMonth(title, rides.map { it.asRow() })
                                },
                        )
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
        // **Both ends, as the kit's row has them** (B-61). This used to be the word "airport" on
        // every row, which is the destination this demo always orders — a list where every line is
        // identical is a list nobody can read. The two ends are coordinates for the same reason R7's
        // are: nothing here geocodes, and a name borrowed from somewhere else would be invented.
        title = "${pickup.asCoordinates()} — ${dropoff.asCoordinates()}",
        // **The kit's route stack** (B-78): both ends as two lines with their pins, which is what
        // keeps every row one height whatever the amount beside it is.
        from = pickup.asCoordinates(),
        to = dropoff.asCoordinates(),
        meta =
            listOfNotNull(
                requestedAtEpochMs?.asDayAndTime(),
                rideClass.name.lowercase(),
                when (status) {
                    RideStatus.CANCELLED -> "cancelled"
                    RideStatus.COMPLETED -> null
                    else -> "in progress"
                },
            ).joinToString(" · "),
        // What paid for it goes under the amount, as the kit's row has it, so the meta stays one line.
        note = paymentMethodId,
        // **A cancelled ride that cost nothing reads `$ 0`, and a ride still running reads `—`**
        // (B-78). The kit's row says the zero; the dash is for a sum that does not exist *yet*.
        amount =
            chargedCents?.let { money(it, quote?.currency ?: "USD") }
                ?: if (status == RideStatus.CANCELLED) money(0, quote?.currency ?: "USD") else "—",
    )

/**
 * The month a ride belongs to, as R9 groups them (B-61).
 *
 * **The client decides both, because both are a locale.** The server sends an instant; a month name
 * and a day are a calendar and a timezone, and this is the half that has them. A ride with no
 * timestamp — nothing in this product produces one, and the field is nullable because the wire is
 * older than it — is grouped under the kit's own fallback rather than dropped.
 */
internal fun RideView.month(): String =
    requestedAtEpochMs?.let {
        val date = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
        "${MONTHS[date.month.number - 1]} ${date.year}"
    } ?: "earlier"

/** `3 september · 09:44`, on the rider's own calendar and clock — R9's rows and R9·b's header (B-79). */
internal fun Long.asDayAndTime(): String {
    val at = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${at.day} ${MONTHS[at.month.number - 1]} · ${at.hour.pad()}:${at.minute.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')

/** Lower case, because the kit's headings are: "metro", not "METRO". */
private val MONTHS =
    listOf(
        "january",
        "february",
        "march",
        "april",
        "may",
        "june",
        "july",
        "august",
        "september",
        "october",
        "november",
        "december",
    )
