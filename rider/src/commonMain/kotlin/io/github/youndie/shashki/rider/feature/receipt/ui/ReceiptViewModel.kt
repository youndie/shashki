package io.github.youndie.shashki.rider.feature.receipt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.shashki.rider.feature.receipt.domain.LoadReceiptUseCase
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
)

public class ReceiptViewModel(
    private val rideId: String,
    private val loadReceipt: LoadReceiptUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiptUiState())
    public val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ReceiptUiState(loading = false, tree = loadReceipt(rideId).getOrNull())
        }
    }
}
