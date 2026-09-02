package io.github.youndie.shashki.rider.feature.promo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.shashki.rider.feature.promo.domain.LoadPromoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The promo screen's state, which is smaller than any other because the screen is not this client's.
 *
 * `tree` is `null` while it is being fetched **and** after a failure, and those are the same thing to
 * this screen: there is no promotion to show. A server-driven screen with no fallback is the one
 * place where "the server did not answer" and "there is nothing to say" are honestly identical.
 */
public data class PromoUiState(
    val loading: Boolean = true,
    val tree: KompotComponent? = null,
)

public class PromoViewModel(
    private val loadPromo: LoadPromoUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromoUiState())
    public val uiState: StateFlow<PromoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tree = loadPromo(Unit).getOrNull()
            _uiState.value = PromoUiState(loading = false, tree = tree)
        }
    }
}
