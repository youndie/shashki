package io.github.youndie.shashki.driver.feature.documents

import io.github.youndie.shashki.driver.feature.documents.domain.DocumentsRepository
import io.github.youndie.shashki.driver.feature.documents.domain.ReadDocumentsUseCase
import io.github.youndie.shashki.driver.feature.documents.domain.UploadDocumentUseCase
import io.github.youndie.shashki.driver.feature.documents.ui.OnboardingUiAction
import io.github.youndie.shashki.driver.feature.documents.ui.OnboardingViewModel
import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DocumentState
import io.github.youndie.shashki.protocol.DriverDocumentView
import io.github.youndie.shashki.protocol.DriverDocumentsView
import io.github.youndie.shashki.ui.screens.OnboardingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D1's view model (B-47).
 *
 * **What is worth asserting here is the mapping, because it is the only place the wire's words
 * become a driver's.** `PENDING` is not "accepted", and the screen must not say it is: the store
 * takes bytes and nobody reviews them, so a green tick after an upload would be exactly the
 * fabricated onboarding this item's rejected alternative describes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the three states reach the screen as three different rows`() =
        runTest(dispatcher) {
            val model = viewModel(FakeDocuments(mixed()))

            val rows = model.uiState.value.documents
            assertEquals(listOf("driving licence", "insurance", "photo of the car"), rows.map { it.title })
            assertEquals(
                listOf(OnboardingState.ACCEPTED, OnboardingState.PENDING, OnboardingState.MISSING),
                rows.map { it.state },
            )
            assertEquals("sent · 2 KB", rows[1].meta, "the size the store reported is not on the screen")
            assertEquals("not sent yet", rows[2].meta)
        }

    /**
     * **An upload answers with the whole view, and the screen takes that answer.** The alternative —
     * flipping the row to `PENDING` locally — would show a state the store has not confirmed, which
     * is the same invention as showing `ACCEPTED`.
     */
    @Test
    fun `a chosen file goes up and the answer is what the screen shows`() =
        runTest(dispatcher) {
            val documents = FakeDocuments(allMissing())
            val model = viewModel(documents, picker = { onPicked -> onPicked(byteArrayOf(1, 2, 3), "image/png") })

            model.onAction(OnboardingUiAction.Choose(index = 0))

            assertEquals(DocumentKind.LICENCE to 3, documents.uploaded)
            assertEquals(
                OnboardingState.PENDING,
                model.uiState.value.documents[0]
                    .state,
            )
        }

    /** A store that refuses says so, and the screen does not pretend the file arrived. */
    @Test
    fun `a refused upload leaves the row alone and stops saying sending`() =
        runTest(dispatcher) {
            val documents = FakeDocuments(allMissing(), failUpload = true)
            val model = viewModel(documents, picker = { onPicked -> onPicked(byteArrayOf(1), "image/png") })

            model.onAction(OnboardingUiAction.Choose(index = 0))

            assertEquals(
                OnboardingState.MISSING,
                model.uiState.value.documents[0]
                    .state,
            )
            assertNull(model.uiState.value.sending, "the screen stayed on 'sending…' after a refusal")
        }

    private fun viewModel(
        documents: DocumentsRepository,
        picker: (onPicked: (ByteArray, String) -> Unit) -> Unit = { },
    ) = OnboardingViewModel(ReadDocumentsUseCase(documents), UploadDocumentUseCase(documents), picker)

    private fun mixed() =
        DriverDocumentsView(
            listOf(
                DriverDocumentView(DocumentKind.LICENCE, DocumentState.ACCEPTED, sizeBytes = 4096),
                DriverDocumentView(DocumentKind.INSURANCE, DocumentState.PENDING, sizeBytes = 2048),
                DriverDocumentView(DocumentKind.CAR_PHOTO, DocumentState.MISSING, sizeBytes = null),
            ),
        )

    private fun allMissing() =
        DriverDocumentsView(DocumentKind.entries.map { DriverDocumentView(it, DocumentState.MISSING, null) })

    private class FakeDocuments(
        private val initial: DriverDocumentsView,
        private val failUpload: Boolean = false,
    ) : DocumentsRepository {
        var uploaded: Pair<DocumentKind, Int>? = null

        override suspend fun states(): DriverDocumentsView = initial

        override suspend fun upload(
            kind: DocumentKind,
            bytes: ByteArray,
            contentType: String,
        ): DriverDocumentsView {
            if (failUpload) error("the store said no")
            uploaded = kind to bytes.size
            return DriverDocumentsView(
                initial.documents.map {
                    if (it.kind == kind) it.copy(state = DocumentState.PENDING, sizeBytes = bytes.size.toLong()) else it
                },
            )
        }
    }
}
