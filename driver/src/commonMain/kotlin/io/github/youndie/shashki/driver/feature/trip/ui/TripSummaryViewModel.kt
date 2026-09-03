package io.github.youndie.shashki.driver.feature.trip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.feature.trip.domain.ReadTripSummaryUseCase
import io.github.youndie.shashki.protocol.TripSummaryView
import io.github.youndie.shashki.protocol.format.asDistance
import io.github.youndie.shashki.protocol.format.asDuration
import io.github.youndie.shashki.protocol.format.money
import io.github.youndie.shashki.ui.screens.DriverTripSummaryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** D5 as the screen holds it: the figures once they are in, and whether they are still coming. */
public data class TripSummaryUiState(
    val loading: Boolean = true,
    val summary: DriverTripSummaryState? = null,
)

/**
 * What the trip that just ended paid (B-70).
 *
 * **It asks again, because the settlement is a saga.** `COMPLETED` is what opens this screen and the
 * payout row is written by the settlement's execution phase a moment later; the first read can land
 * in between and get 404, which here means *not yet* rather than *never*. A handful of retries at
 * one second is the honest shape of that gap; after them the screen says so.
 *
 * Nothing is computed here: the server added the money up and this turns cents into the kit's
 * figures, exactly as D6 does.
 */
public class TripSummaryViewModel(
    private val rideId: String,
    private val readSummary: ReadTripSummaryUseCase,
    /** Where the read runs; `null` is this view model's own scope. See `TripViewModel` for why. */
    loopScope: CoroutineScope? = null,
    private val retryAfter: Duration = RETRY,
) : ViewModel() {
    private val scope: CoroutineScope = loopScope ?: viewModelScope
    private val _uiState = MutableStateFlow(TripSummaryUiState())
    public val uiState: StateFlow<TripSummaryUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repeat(ATTEMPTS) { attempt ->
                readSummary(rideId).onSuccess {
                    _uiState.value = TripSummaryUiState(loading = false, summary = it.asState())
                    return@launch
                }
                if (attempt < ATTEMPTS - 1) delay(retryAfter)
            }
            _uiState.value = TripSummaryUiState(loading = false, summary = null)
        }
    }

    private companion object {
        const val ATTEMPTS = 5
        val RETRY = 1.seconds
    }
}

/**
 * The kit's D5, line by line: the share as the figure, then fare, fee, tip and the leg.
 *
 * **The fee is a line and not a footnote** — "shown, never hidden" is the kit's own sentence — and it
 * is named with the percentage the server sent rather than one this client knows.
 */
internal fun TripSummaryView.asState(): DriverTripSummaryState =
    DriverTripSummaryState(
        earned = "+${money(payoutCents + tipCents, currency)}",
        meta =
            listOfNotNull(
                paymentMethodId.takeIf {
                    it.isNotBlank()
                },
                "today ${money(todayCents, currency)}",
            ).joinToString(" · "),
        lines =
            buildList {
                add("fare" to money(fareCents, currency))
                // A hyphen-minus and not U+2212: the bundled face has no minus sign, and
                // `GlyphCoverageTest` is what says so rather than the host's fallback font.
                add("service fee $feePercent %" to "-${money(feeCents, currency)}")
                if (tipCents > 0) add("tip" to money(tipCents, currency))
                add("${durationSeconds.asDuration()} · ${distanceMetres.asDistance()}" to "—")
            },
    )
