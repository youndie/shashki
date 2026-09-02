package io.github.youndie.shashki.driver.feature.documents.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.shashki.driver.feature.documents.domain.ReadDocumentsUseCase
import io.github.youndie.shashki.driver.feature.documents.domain.UploadDocumentUseCase
import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DocumentState
import io.github.youndie.shashki.protocol.DriverDocumentView
import io.github.youndie.shashki.ui.screens.OnboardingDocument
import io.github.youndie.shashki.ui.screens.OnboardingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** D1 as the screen holds it. */
public data class OnboardingUiState(
    val loading: Boolean = true,
    val documents: List<OnboardingDocument> = emptyList(),
    val sending: DocumentKind? = null,
)

public sealed interface OnboardingUiAction {
    /** The person tapped a field. The file dialog is the platform's; the bytes come back here. */
    public data class Choose(
        val index: Int,
    ) : OnboardingUiAction
}

public sealed interface OnboardingUiEvent {
    public data class Failed(
        val message: String,
    ) : OnboardingUiEvent
}

/**
 * The driver's documents (B-47).
 *
 * **The picker is a port and the upload is a use case**, which keeps the one platform-specific line
 * out of everything else: a browser opens a file dialog, a desktop window has nobody to ask, and the
 * view model does not know which it is talking to.
 */
public class OnboardingViewModel(
    private val readDocuments: ReadDocumentsUseCase,
    private val uploadDocument: UploadDocumentUseCase,
    private val picker: (onPicked: (ByteArray, String) -> Unit) -> Unit = ::pickDocument,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    public val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = Channel<OnboardingUiEvent>(Channel.BUFFERED)
    public val events: Flow<OnboardingUiEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    public fun onAction(action: OnboardingUiAction) {
        when (action) {
            is OnboardingUiAction.Choose -> {
                val kind = DocumentKind.entries.getOrNull(action.index) ?: return
                _uiState.value = _uiState.value.copy(sending = kind)
                picker { bytes, contentType -> send(kind, bytes, contentType) }
            }
        }
    }

    private fun send(
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    ) {
        viewModelScope.launch {
            uploadDocument(UploadDocumentUseCase.Params(kind, bytes, contentType))
                .onSuccess { view ->
                    _uiState.value = OnboardingUiState(loading = false, documents = view.documents.map { it.asRow() })
                }.onFailure {
                    _uiState.value = _uiState.value.copy(sending = null)
                    _events.send(OnboardingUiEvent.Failed(it.message ?: "that did not reach the store"))
                }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            readDocuments(Unit)
                .onSuccess { view ->
                    _uiState.value = OnboardingUiState(loading = false, documents = view.documents.map { it.asRow() })
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loading = false)
                    _events.send(OnboardingUiEvent.Failed(it.message ?: "the documents could not be read"))
                }
        }
    }
}

/** The wire's three kinds as the words a driver reads, and its states as the kit's. */
internal fun DriverDocumentView.asRow(): OnboardingDocument =
    OnboardingDocument(
        title =
            when (kind) {
                DocumentKind.LICENCE -> "driving licence"
                DocumentKind.INSURANCE -> "insurance"
                DocumentKind.CAR_PHOTO -> "photo of the car"
            },
        meta =
            when (state) {
                DocumentState.MISSING -> "not sent yet"

                // The size is the store's own answer about the object, which is the only fact this
                // screen can show about a file it cannot draw.
                DocumentState.PENDING -> "sent · ${(sizeBytes ?: 0) / KIB} KB"

                DocumentState.ACCEPTED -> "checked"
            },
        state =
            when (state) {
                DocumentState.MISSING -> OnboardingState.MISSING
                DocumentState.PENDING -> OnboardingState.PENDING
                DocumentState.ACCEPTED -> OnboardingState.ACCEPTED
            },
    )

private const val KIB = 1024
