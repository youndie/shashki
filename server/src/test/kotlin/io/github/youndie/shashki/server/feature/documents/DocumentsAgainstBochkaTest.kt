package io.github.youndie.shashki.server.feature.documents

import io.github.youndie.shashki.protocol.DocumentKind
import io.github.youndie.shashki.protocol.DocumentState
import io.github.youndie.shashki.protocol.DriverDocuments
import io.github.youndie.shashki.protocol.DriverDocumentsView
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A driver's documents, against the object store the tiles come from (B-47).
 *
 * **This is the one scenario that gives an S3 *client* a reason to exist in this product.** D12
 * settled that bochka is a host rather than a dependency: a browser reads the tile archive over
 * public ranged HTTP, and a Kotlin client would have nothing to do. A licence is the other case —
 * a browser cannot sign SigV4 without holding the secret that signs it, so the upload comes through
 * the server, and the server writes it with s3kn into a bucket nobody published.
 *
 * **The last assertion is the whole difference between the two.** The tiles' bucket is public
 * because B-07 measured that a browser has no other way in; this one is not, and an anonymous `GET`
 * for a licence is refused.
 *
 * ```bash
 * docker compose -f docker/compose.yaml up -d bochka && bash docker/bootstrap-documents.sh
 * SHASHKI_BOCHKA=http://127.0.0.1:19000 ./gradlew :server:test --tests '*DocumentsAgainstBochka*'
 * ```
 */
class DocumentsAgainstBochkaTest {
    private val endpoint: String? = System.getenv("SHASHKI_BOCHKA")

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `three documents are written to the store, read back through the server, and refused to anybody else`() =
        testApplication {
            assumeTrue(endpoint != null, "SHASHKI_BOCHKA is not set: the object store is not up")
            application {
                shashki(
                    PostgresHarness.database,
                    documents =
                        DocumentsConfig.store(HttpClient(CIO)) { name ->
                            when (name) {
                                DocumentsConfig.ENDPOINT_VARIABLE -> endpoint
                                DocumentsConfig.KEY_VARIABLE -> System.getenv("BOCHKA_KEY") ?: "shashki"
                                DocumentsConfig.SECRET_VARIABLE -> System.getenv("BOCHKA_SECRET") ?: "shashkisecret"
                                DocumentsConfig.BUCKET_VARIABLE -> "documents"
                                else -> null
                            }
                        },
                )
            }
            val client = typedClient()
            startApplication()

            DocumentKind.entries.forEach { kind ->
                val response =
                    client.post(DriverDocuments.OfKind(kind = kind, driverId = DRIVER)) {
                        setBody(bytesFor(kind))
                    }
                assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
            }

            val states = client.get(DriverDocuments(driverId = DRIVER)).body<DriverDocumentsView>()
            assertEquals(
                DocumentKind.entries.toList(),
                states.documents.map { it.kind },
                "the screen is given one row per document the service asks for",
            )
            assertTrue(
                states.documents.all { it.state == DocumentState.PENDING },
                "an uploaded document is pending; nothing here accepts one, and DocumentState says so",
            )
            assertEquals(
                bytesFor(DocumentKind.LICENCE).size.toLong(),
                states.documents.first { it.kind == DocumentKind.LICENCE }.sizeBytes,
                "the size came from somewhere other than the store's own listing",
            )

            // Back through the server: the same bytes, which is what says the write reached bochka
            // rather than a buffer somewhere in this process.
            val read = client.get(DriverDocuments.OfKind(kind = DocumentKind.LICENCE, driverId = DRIVER))
            assertContentEquals(bytesFor(DocumentKind.LICENCE), read.bodyAsBytes())

            // And straight at the store, unsigned, the way a browser would: refused.
            val anonymous = HttpClient(CIO) { expectSuccess = false }
            val direct = anonymous.get("$endpoint/documents/drivers/$DRIVER/${DocumentKind.LICENCE.name}")
            assertTrue(
                direct.status == HttpStatusCode.Forbidden || direct.status == HttpStatusCode.Unauthorized,
                "a licence was readable by anybody who guessed the key: ${direct.status}",
            )
            anonymous.close()
        }

    /** Different lengths per kind, so a test that mixed two objects up would fail on the size. */
    private fun bytesFor(kind: DocumentKind) = ByteArray(16 + kind.ordinal) { (it + kind.ordinal).toByte() }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
            expectSuccess = false
        }

    private companion object {
        const val DRIVER = "driver-docs"
    }
}
