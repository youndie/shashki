package io.github.youndie.shashki.server.feature.documents

import io.github.youndie.shashki.protocol.DriverDocuments
import io.github.youndie.shashki.protocol.DriverDocumentsView
import io.github.youndie.shashki.server.feature.auth.driverIdentity
import io.github.youndie.shashki.server.feature.documents.domain.DocumentStore
import io.github.youndie.shashki.server.feature.documents.domain.NoStoreConfiguredException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveStream
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * The driver's documents: `GET /api/driver/documents`, and `POST`/`GET .../{kind}` (B-47).
 *
 * **Auth tier: the driver's token** — these routes are declared inside the same protected block as
 * the rest of the driver's, so a licence is never readable by whoever knows an id. The `driverId`
 * query parameter is the provider-less demo's only source, ignored the moment there is a principal;
 * `driverIdentity` says why it replaces rather than compares.
 *
 * **The upload goes through this server and never straight to the store**, which is the whole point
 * of the feature: a browser cannot sign SigV4 without holding the secret that signs it, so the file
 * arrives here and this server is the object store's only client (D12).
 */
public fun Route.documentRoutes() {
    val documents by inject<DocumentStore>()

    get<DriverDocuments> { route ->
        val driverId = call.driverIdentity(route.driverId)
        call.respond(DriverDocumentsView(documents.states(driverId)))
    }

    post<DriverDocuments.OfKind> { route ->
        if (!documents.configured) throw NoStoreConfiguredException()
        val driverId = call.driverIdentity(route.driverId)
        // **Bounded rather than streamed.** A licence is a photograph and the cap is what stops a
        // client filling somebody else's bucket; s3kn streams a large object properly, and a route
        // that accepted one without a limit would be the hole rather than the feature.
        val bytes = call.receiveStream().readNBytes(MAX_DOCUMENT_BYTES + 1)
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "a document over ${MAX_DOCUMENT_BYTES / KIB} KiB" }
        require(bytes.isNotEmpty()) { "an empty document" }

        documents.put(driverId, route.kind, bytes, call.request.contentType().toString())
        call.respond(HttpStatusCode.Accepted, DriverDocumentsView(documents.states(driverId)))
    }

    // Read back through the server, because the bucket refuses an anonymous reader — which is the
    // fact `DocumentsAgainstBochkaTest` asserts from the other side.
    get<DriverDocuments.OfKind> { route ->
        val driverId = call.driverIdentity(route.driverId)
        val bytes = documents.read(driverId, route.kind) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }
}

/** Two megabytes: a photograph of a licence, and nothing that could be a video. */
private const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
private const val KIB = 1024
