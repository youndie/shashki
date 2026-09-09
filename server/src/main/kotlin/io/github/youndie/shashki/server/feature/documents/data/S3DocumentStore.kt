package io.github.youndie.shashki.server.feature.documents.data

import io.github.youndie.s3.S3Client
import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DocumentState
import io.github.youndie.shashki.protocol.DriverDocumentView
import io.github.youndie.shashki.server.feature.documents.domain.DocumentStore
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.toList
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException

/**
 * The documents in an S3-compatible store, written and read with s3kn (B-47).
 *
 * **This is the whole reason an S3 *client* exists in this product.** The tiles are read by a browser
 * over public ranged HTTP — [D12](../../../../../../../../../docs/research/research-architecture.md)
 * settled that bochka is a host there rather than a dependency, and a Kotlin client would have had
 * nothing to do. A document is the other case: a browser cannot sign SigV4 without the secret that
 * signs it, so the file comes here and this writes it, authenticated, to a bucket that is **not**
 * public — which is the difference the store's own policy makes visible.
 *
 * The key is `drivers/<driverId>/<kind>`. No extension: what a file claims to be is a header the
 * uploader chose, and a store that trusted it would be naming objects after a client's opinion.
 */
public class S3DocumentStore(
    private val client: S3Client,
    private val bucket: String,
) : DocumentStore {
    override val configured: Boolean = true

    override suspend fun put(
        driverId: String,
        kind: DocumentKind,
        bytes: ByteArray,
        contentType: String,
    ) {
        client.put(bucket, key(driverId, kind), bytes, contentType = contentType)
    }

    override suspend fun read(
        driverId: String,
        kind: DocumentKind,
    ): ByteArray? =
        @Suppress(
            "ktlint:kapkan:swallowed-failure",
            "документа нет — это и есть ответ `null`, который экран показывает как «не загружен»",
        )
        try {
            client.get(bucket, key(driverId, kind)) { it.body.readRemaining().readByteArray() }
        } catch (e: CancellationException) {
            // A cancelled read is not a document that is not there, and `null` here means exactly
            // "not there" to the screen that asked.
            throw e
        } catch (_: Throwable) {
            null
        }

    /**
     * One listing rather than three `head`s: the states of all three documents are one screen, and
     * a prefix listing is the one request that answers it.
     */
    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "листинг не ответил — экран показывает то же, что и при пустом бакете, и это осознанно",
    )
    override suspend fun states(driverId: String): List<DriverDocumentView> {
        val prefix = "drivers/$driverId/"
        val sizes =
            try {
                client
                    .list(bucket, prefix = prefix)
                    .toList()
                    .flatMap { it.objects }
                    .associate { it.key.removePrefix(prefix) to it.size }
            } catch (e: CancellationException) {
                // An empty map draws the screen as "nothing uploaded", which is a different answer
                // from "the listing was called off".
                throw e
            } catch (_: Throwable) {
                emptyMap()
            }

        return DocumentKind.entries.map { kind ->
            val size = sizes[kind.name]
            DriverDocumentView(
                kind = kind,
                // **Two states, not three.** Uploaded is `PENDING`; nothing here accepts anything,
                // because accepting is a person and a queue. `DocumentState` says so where it is
                // declared, and the screen draws the third because the artboard has it.
                state = if (size == null) DocumentState.MISSING else DocumentState.PENDING,
                sizeBytes = size,
            )
        }
    }

    private fun key(
        driverId: String,
        kind: DocumentKind,
    ) = "drivers/$driverId/${kind.name}"
}
