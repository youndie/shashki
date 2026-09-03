package io.github.youndie.shashki.rider.feature.receipt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.shashki.rider.feature.history.ui.asDayAndTime
import io.github.youndie.shashki.rider.feature.receipt.domain.LoadReceiptUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.ReadRideUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * R9·b as the screen holds it (B-61).
 *
 * **`tree` and nothing else, deliberately.** There is no fare here, no total and no currency: this
 * client is not allowed to know what the numbers mean, only how to draw the card it was sent. A
 * state with a `Receipt` object in it would be the first step towards a client that adds something
 * up.
 *
 * `null` after a failure and `null` while loading are told apart by [loading], because a ride that
 * has not settled yet has no receipt *and no error* — the server's 404 is an answer.
 */
public data class ReceiptUiState(
    val loading: Boolean = true,
    val tree: KompotComponent? = null,
    /**
     * `3 september · 09:44` — when the ride was asked for, above the server's card (B-79).
     *
     * **The one thing on this screen the client says**, because a date is a calendar and a timezone
     * and the browser has both (B-61). It is read off the ride, not off the tree, and drawn as a
     * native header over the tree rather than sent as text the server would have had to format.
     */
    val when_: String? = null,
)

public class ReceiptViewModel(
    private val rideId: String,
    private val loadReceipt: LoadReceiptUseCase,
    private val readRide: ReadRideUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiptUiState())
    public val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tree = loadReceipt(rideId).getOrNull()
            val requestedAt = readRide(rideId).getOrNull()?.requestedAtEpochMs
            _uiState.value = ReceiptUiState(loading = false, tree = tree, when_ = requestedAt?.asDayAndTime())
        }
    }
}
