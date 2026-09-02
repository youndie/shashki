package io.github.youndie.shashki.auth

import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.http.URLBuilder
import io.ktor.resources.href
import io.ktor.resources.serialization.ResourcesFormat
import ru.workinprogress.shildik.shared.OAuth2
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
    public fun authorizeUrl(): String {
        // **The path is shildik's own `@Resource`, and the query is Ktor's builder.** Neither half is
        // written as a string any more: a renamed route upstream is a compile error here, and the
        // percent-encoding is the one the rest of the stack uses rather than eleven lines of this
        // module's own. Both used to be hand-made and both are gone with youndie/shildik#20.
        val builder = URLBuilder(config.issuer)
        href(ResourcesFormat(), OAuth2.Authorize(OAuth2(realm = config.realm)), builder)
        builder.parameters.apply {
            append("response_type", "code")
            append("client_id", config.clientId)
            append("redirect_uri", config.redirectUri)
            append("scope", config.scope)
            append("state", state)
            append("nonce", nonce)
            append("code_challenge", challenge.value)
            append("code_challenge_method", challenge.method)
        }
        return builder.buildString()
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
