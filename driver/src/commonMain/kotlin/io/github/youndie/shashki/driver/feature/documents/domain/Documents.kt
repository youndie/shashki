package io.github.youndie.shashki.driver.feature.documents.domain

import io.github.youndie.shashki.driver.UseCase
import io.github.youndie.shashki.driver.suspendRunCatching
import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DriverDocumentsView

/** The documents this driver has sent, and the one call that sends another (B-47). */
public interface DocumentsRepository {
    public suspend fun states(): DriverDocumentsView

    public suspend fun upload(
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    ): DriverDocumentsView
}

public class ReadDocumentsUseCase(
    private val documents: DocumentsRepository,
) : UseCase<Unit, DriverDocumentsView> {
    override suspend fun invoke(params: Unit): Result<DriverDocumentsView> = suspendRunCatching { documents.states() }
}

/**
 * Send one document.
 *
 * **The bytes go to this product's server and never to the object store**, which is the whole shape
 * of the feature: a browser cannot sign SigV4 without holding the secret that signs it, so the file
 * travels one hop further than a reader would expect and the server is the store's only client.
 */
public class UploadDocumentUseCase(
    private val documents: DocumentsRepository,
) : UseCase<UploadDocumentUseCase.Params, DriverDocumentsView> {
    override suspend fun invoke(params: Params): Result<DriverDocumentsView> =
        suspendRunCatching { documents.upload(params.kind, params.bytes, params.contentType) }

    public class Params(
        public val kind: DocumentKind,
        public val bytes: ByteArray,
        public val contentType: String,
    )
}
