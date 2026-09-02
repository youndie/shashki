package io.github.youndie.shashki.driver.feature.documents.data

import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.feature.documents.domain.DocumentsRepository
import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DriverDocuments
import io.github.youndie.shashki.protocol.DriverDocumentsView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** `GET/POST /api/driver/documents`. The id travels only for the provider-less demo (B-52). */
public class HttpDocumentsRepository(
    private val client: HttpClient,
    private val identity: DriverIdentity,
) : DocumentsRepository {
    override suspend fun states(): DriverDocumentsView =
        client.get(DriverDocuments(driverId = identity.current())).body()

    override suspend fun upload(
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    ): DriverDocumentsView =
        client
            .post(DriverDocuments.OfKind(kind = kind, driverId = identity.current())) {
                // The file's own type, as the browser reported it — the server stores it beside the
                // object and never trusts it as a name.
                contentType(ContentType.parse(contentType))
                setBody(bytes)
            }.body()
}
