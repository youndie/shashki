package io.github.youndie.shashki.auth

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.ExperimentalEncodingApi

/** Where the provider is and who we are to it. Everything here is public by definition. */
public data class SignInConfig(
    val issuer: String,
    val realm: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String = "openid profile email",
)

/**
 * One sign-in attempt, from the redirect out to the token request back.
 *
 * **It holds the verifier and never yields it to the redirect.** [authorizeUrl] is built from
 * [challenge] and [state]; the verifier appears only in [tokenForm], which is a direct POST from
 * this tab to the provider. That is not a convention to remember — [authorizeUrl] has no access to
 * a verifier in a form it could put in a URL, because [CodeVerifier] is a distinct type and nothing
 * in this file converts one into a parameter value except [tokenForm].
 *
 * **Which sign-in method the person uses is not decided here.** shildik's `authorize` serves its own
 * page and the choice between a magic link and Google is made on it, coming back through
 * `callback/{method}`. So there is no `method` parameter in this flow and no branch for Google: the
 * client's side of "sign in with Google" is the same redirect as the client's side of "sign in with
 * a magic link".
 */
public class SignInAttempt internal constructor(
    private val config: SignInConfig,
    private val verifier: CodeVerifier,
    public val challenge: CodeChallenge,
    public val state: String,
    public val nonce: String,
) {
    /**
     * The address to send the browser to. Parameter names are the ones shildik reads out of the
     * query — `client_id`, `redirect_uri`, `response_type`, `scope`, `state`, `nonce`,
     * `code_challenge`, `code_challenge_method` — read from its route rather than from memory of
     * the RFC, because a provider that spelled one differently would fail at run time only.
     */
    public fun authorizeUrl(): String =
        buildString {
            append(config.issuer.trimEnd('/'))
            append("/realms/")
            append(config.realm.encodeUrlComponent())
            append("/oauth2/authorize?")
            append(
                listOf(
                    "response_type" to "code",
                    "client_id" to config.clientId,
                    "redirect_uri" to config.redirectUri,
                    "scope" to config.scope,
                    "state" to state,
                    "nonce" to nonce,
                    "code_challenge" to challenge.value,
                    "code_challenge_method" to challenge.method,
                ).joinToString("&") { (key, value) -> "$key=${value.encodeUrlComponent()}" },
            )
        }

    /**
     * The form body of the code exchange — the only place the verifier is used, and it goes to the
     * token endpoint rather than through the address bar.
     *
     * [returnedState] is checked here rather than by the caller. A callback whose `state` does not
     * match this attempt is somebody else's callback, and the natural place to forget that check is
     * a caller that has the code in its hand and wants to spend it.
     */
    public fun tokenForm(
        code: String,
        returnedState: String,
    ): Map<String, String> {
        require(returnedState == state) { "this callback belongs to a different sign-in attempt" }
        return mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "client_id" to config.clientId,
            "code_verifier" to verifier.value,
        )
    }

    public companion object {
        /** A new attempt: fresh verifier, its challenge, and fresh `state` and `nonce`. */
        public suspend fun begin(config: SignInConfig): SignInAttempt {
            val verifier = Pkce.newVerifier()
            return SignInAttempt(
                config = config,
                verifier = verifier,
                challenge = Pkce.challenge(verifier),
                state = randomToken(),
                nonce = randomToken(),
            )
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun randomToken(): String = base64Url.encode(CryptographyRandom.nextBytes(TOKEN_BYTES))

        private const val TOKEN_BYTES = 16
    }
}

/**
 * Percent-encoding for a query value, written out because this module has no HTTP client to borrow
 * one from — and taking a Ktor dependency to escape a string would put a transport in a module whose
 * whole point is that it has none.
 *
 * Unreserved set from RFC 3986 §2.3. Everything else is encoded, including `+` and `/`, which is the
 * difference that matters: a base64url value contains `-` and `_` and never `+` or `/`, but a
 * `redirect_uri` contains both.
 */
private fun String.encodeUrlComponent(): String =
    buildString {
        for (byte in this@encodeUrlComponent.encodeToByteArray()) {
            val char = byte.toInt().toChar()
            if (char.isUnreserved()) {
                append(char)
            } else {
                append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(
                    HEX[
                        byte.toInt() and
                            0xF,
                    ],
                )
            }
        }
    }

private fun Char.isUnreserved(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

private const val HEX = "0123456789ABCDEF"
