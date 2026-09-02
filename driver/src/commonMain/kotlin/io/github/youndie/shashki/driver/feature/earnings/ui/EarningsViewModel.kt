package io.github.youndie.shashki.driver.feature.earnings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.feature.earnings.domain.ReadEarningsUseCase
import io.github.youndie.shashki.protocol.EarningsView
import io.github.youndie.shashki.ui.format.money
import io.github.youndie.shashki.ui.kompot.EarningsTile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** D6 as the screen holds it: today's figure, the tiles, and whether the answer is in. */
public data class EarningsUiState(
    val loading: Boolean = true,
    val today: String = "",
    val tiles: List<EarningsTile> = emptyList(),
)

public sealed interface EarningsUiEvent {
    public data class Failed(
        val message: String,
    ) : EarningsUiEvent
}

/**
 * What the driver has earned (B-46).
 *
 * **Sums of payout rows, formatted here and computed nowhere.** The server adds up what it wrote
 * down as owed; this turns cents into the kit's figures. A client that recomputed a share from fares
 * would agree with the server until the first rolled-back tip.
 */
public class EarningsViewModel(
    private val readEarnings: ReadEarningsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EarningsUiState())
    public val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()

    private val _events = Channel<EarningsUiEvent>(Channel.BUFFERED)
    public val events: Flow<EarningsUiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            readEarnings(Unit)
                .onSuccess { _uiState.value = it.asState() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loading = false)
                    _events.send(EarningsUiEvent.Failed(it.message ?: "the earnings could not be read"))
                }
        }
    }
}

/**
 * The kit's grid: today asks for the screen's one accent surface, the others take chrome.
 *
 * Sizes are `2` — half the four-column grid each — because two numbers side by side is what D6 draws
 * and `EarningsTile.ALLOWED_SIZES` is what the renderer will accept.
 */
internal fun EarningsView.asState(): EarningsUiState =
    EarningsUiState(
        loading = false,
        today = money(todayCents, currency),
        tiles =
            listOf(
                EarningsTile("today", "today", money(todayCents, currency), size = 2, accent = true),
                EarningsTile("week", "week", money(weekCents, currency), size = 2),
                EarningsTile("all", "all time", money(allTimeCents, currency), size = 2),
            ),
    )
