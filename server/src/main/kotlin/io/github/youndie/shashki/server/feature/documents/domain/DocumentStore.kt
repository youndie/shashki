package io.github.youndie.shashki.server.feature.documents.domain

import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DocumentState
import io.github.youndie.shashki.protocol.DriverDocumentView

/**
 * Where a driver's documents live (B-47).
 *
 * **A port, because the store is the one thing this feature is about.** The implementation writes to
 * an S3-compatible service with s3kn; the absence of one is a running configuration and answers
 * `MISSING` for everything, which is what a demo pointed at no store honestly has.
 */
public interface DocumentStore {
    public suspend fun put(
        driverId: String,
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    )

    /** The bytes back, through this server — the store itself refuses an anonymous reader. */
    public suspend fun read(
        driverId: String,
        kind: DocumentKind,
    ): ByteArray?

    public suspend fun states(driverId: String): List<DriverDocumentView>

    /** Whether a store is configured at all. `false` is the demo, and the screen is told. */
    public val configured: Boolean
}

/** Nothing configured: every document is missing and no upload is accepted. */
public object NoDocumentStore : DocumentStore {
    override val configured: Boolean = false

    override suspend fun put(
        driverId: String,
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    ): Unit = throw NoStoreConfiguredException()

    override suspend fun read(
        driverId: String,
        kind: DocumentKind,
    ): ByteArray? = null

    override suspend fun states(driverId: String): List<DriverDocumentView> =
        DocumentKind.entries.map { DriverDocumentView(it, DocumentState.MISSING) }
}

/** Asked to store something with nowhere to put it. A 503 rather than a 500: it is configuration. */
public class NoStoreConfiguredException : RuntimeException("no object store is configured for documents")
