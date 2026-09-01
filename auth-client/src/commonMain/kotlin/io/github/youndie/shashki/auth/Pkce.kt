package io.github.youndie.shashki.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline

/**
 * The secret half of a sign-in, and the one thing that must never be sent to the authorization
 * endpoint.
 *
 * A browser application cannot hold a client secret: everything it ships is readable. PKCE replaces
 * the secret with a value invented per sign-in — the *verifier* — of which only a hash, the
 * *challenge*, travels with the redirect. An intercepted authorization code is then worth nothing
 * without the verifier, which never left the tab that started the flow.
 *
 * A value class rather than a `String` so that "the thing that may be sent" and "the thing that may
 * not" are different types, and putting the wrong one in a redirect is a compile error.
 */
@JvmInline
public value class CodeVerifier internal constructor(
    public val value: String,
) {
    init {
        // RFC 7636 §4.1. Below 43 characters the verifier does not carry the entropy the rest of
        // the scheme assumes; above 128 servers are not required to accept it.
        require(value.length in MIN_LENGTH..MAX_LENGTH) {
            "a code verifier is ${MIN_LENGTH}..$MAX_LENGTH characters, this one is ${value.length}"
        }
    }

    public companion object {
        public const val MIN_LENGTH: Int = 43
        public const val MAX_LENGTH: Int = 128
    }
}

/** The public half: what the authorization endpoint is told, and how it was derived. */
public data class CodeChallenge(
    val value: String,
    val method: String,
)

/**
 * Generating the pair — the half of PKCE that belongs to the client.
 *
 * shildik has the other half (`ru.workinprogress.shildik.crypto.Pkce.matches`) and nothing else:
 * verifying is the provider's job and generating is ours, so there is no code here that duplicates
 * code there. The primitive is deliberately the same library, so the two halves agree by
 * construction rather than by coincidence.
 */
public object Pkce {
    /**
     * **`S256`, and no way to ask for anything else.**
     *
     * RFC 7636 also defines `plain`, where the challenge *is* the verifier — which protects nothing,
     * since intercepting the redirect then hands over the secret it was supposed to hide. shildik
     * rejects it server-side with `only S256 is supported`; this module cannot express it at all,
     * which is the stronger of the two guarantees because it needs no round trip to hold.
     */
    public const val METHOD_S256: String = "S256"

    private val sha256 by lazy { CryptographyProvider.Default.get(SHA256).hasher() }

    /**
     * A fresh verifier, from the platform's cryptographic random — `SecureRandom` on the JVM,
     * `crypto.getRandomValues` in a browser.
     *
     * 32 bytes, which base64url-encodes to exactly 43 characters: the shortest verifier the RFC
     * allows, and the shortest one carrying a full 256 bits. A longer string here would be more
     * characters, not more entropy.
     */
    @OptIn(ExperimentalEncodingApi::class)
    public fun newVerifier(): CodeVerifier =
        CodeVerifier(base64Url.encode(CryptographyRandom.nextBytes(VERIFIER_BYTES)))

    /**
     * `BASE64URL(SHA256(ASCII(verifier)))`, per RFC 7636 §4.2.
     *
     * `suspend` because in a browser this is WebCrypto, which is asynchronous and cannot be made
     * otherwise. Hiding that behind a blocking call on the JVM and a busy wait in the browser would
     * put the platform difference inside the function instead of in its signature.
     */
    @OptIn(ExperimentalEncodingApi::class)
    public suspend fun challenge(verifier: CodeVerifier): CodeChallenge =
        CodeChallenge(
            value = base64Url.encode(sha256.hash(verifier.value.encodeToByteArray())),
            method = METHOD_S256,
        )

    private const val VERIFIER_BYTES = 32
}

/**
 * Base64url **without padding**, which is what RFC 7636 §A means by "base64url" and is not the
 * default: `=` is not in the unreserved set, so a padded value travels differently depending on
 * whether whoever built the URL escaped it.
 */
@OptIn(ExperimentalEncodingApi::class)
internal val base64Url: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
