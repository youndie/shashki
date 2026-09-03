package io.github.youndie.shashki.driver.feature.documents.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.youndie.shashki.ui.screens.DriverOnboarding
import org.koin.compose.viewmodel.koinViewModel

/** D1, with the store behind it. */
@Composable
public fun OnboardingScreen(
    onFailed: (String) -> Unit,
    /** How to leave, or `null` where the platform offers it (B-67). */
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingUiEvent.Failed -> onFailed(event.message)
            }
        }
    }

    OnboardingContent(uiState, viewModel::onAction, modifier, onBack)
}

@Composable
public fun OnboardingContent(
    uiState: OnboardingUiState,
    onAction: (OnboardingUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    DriverOnboarding(
        documents = uiState.documents,
        uploadLabel = if (uiState.sending != null) "sending…" else "choose a file",
        // **The note says what the product does not do.** Nothing here accepts a document; a screen
        // that implied somebody was reviewing would be the fabricated onboarding this item's own
        // rejected alternative names.
        note = "three documents, and nothing here reviews them yet",
        onUpload = { index -> onAction(OnboardingUiAction.Choose(index)) },
        modifier = modifier,
        onBack = onBack,
    )
}
