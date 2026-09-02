package io.github.youndie.shashki.server.feature.documents

import io.github.youndie.s3.AddressingStyle
import io.github.youndie.s3.S3Client
import io.github.youndie.s3.S3Config
import io.github.youndie.s3.S3Credentials
import io.github.youndie.s3.S3Endpoint
import io.github.youndie.shashki.server.feature.documents.data.S3DocumentStore
import io.github.youndie.shashki.server.feature.documents.domain.DocumentStore
import io.github.youndie.shashki.server.feature.documents.domain.NoDocumentStore
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory

/**
 * Where a driver's documents go, or the fact that they go nowhere (B-47).
 *
 * **Absent by default and loud about it**, like every other optional dependency here. A demo with no
 * object store still runs: the onboarding screen shows three missing documents and says why, which
 * is a truthful screen rather than an upload button that swallows files.
 *
 * **The bucket is not the tiles' bucket and must not be public.** B-07 made the tiles readable by
 * anybody because a browser cannot sign a request; a licence is the opposite requirement, and the
 * difference between the two is the bucket's policy rather than anything in this code — which is
 * why the test that matters asserts an anonymous `GET` is refused.
 */
public object DocumentsConfig {
    public const val ENDPOINT_VARIABLE: String = "SHASHKI_DOCUMENTS_ENDPOINT"
    public const val KEY_VARIABLE: String = "SHASHKI_DOCUMENTS_KEY"
    public const val SECRET_VARIABLE: String = "SHASHKI_DOCUMENTS_SECRET"
    public const val BUCKET_VARIABLE: String = "SHASHKI_DOCUMENTS_BUCKET"

    private val LOG = LoggerFactory.getLogger(DocumentsConfig::class.java)

    public fun store(
        http: HttpClient,
        env: (String) -> String? = System::getenv,
    ): DocumentStore {
        val endpoint = env(ENDPOINT_VARIABLE)?.takeIf { it.isNotBlank() }
        val key = env(KEY_VARIABLE)?.takeIf { it.isNotBlank() }
        val secret = env(SECRET_VARIABLE)?.takeIf { it.isNotBlank() }
        if (endpoint == null || key == null || secret == null) {
            LOG.warn(
                "no {}/{}/{}: a driver cannot upload anything and the onboarding screen says so",
                ENDPOINT_VARIABLE,
                KEY_VARIABLE,
                SECRET_VARIABLE,
            )
            return NoDocumentStore
        }

        val config =
            S3Config(
                endpoint = S3Endpoint.parse(endpoint),
                region = "us-east-1",
                credentials = S3Credentials(accessKeyId = key, secretAccessKey = secret),
                // **Path addressing, because the stand's store is an address rather than a name.**
                // `127.0.0.1` cannot carry a bucket as a DNS label — s3kn's own note, and B-07 met
                // the same thing from the tile side.
                addressingStyle = AddressingStyle.PATH,
                // The bodies here are in memory and therefore hashed, so this changes nothing today;
                // it is set because the stand speaks plain HTTP and a streamed upload would
                // otherwise be refused rather than silently unprotected.
                allowUnsignedPayloadOverHttp = endpoint.startsWith("http://"),
            )
        return S3DocumentStore(S3Client(config, http), env(BUCKET_VARIABLE)?.takeIf { it.isNotBlank() } ?: "documents")
    }
}
