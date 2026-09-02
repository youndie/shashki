package io.github.youndie.shashki.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `SignInAttempt` against a shildik that is actually running.
 *
 * **This is the half of B-26 that does not need a browser**, and it is the half that can be wrong in
 * a way unit tests cannot see: the URL this module builds has to be the one the provider routes, the
 * challenge it computes has to be the one the provider verifies, and the form it sends has to be the
 * one the token endpoint accepts. Every one of those is an agreement with somebody else's code.
 *
 * The browser's part — that WebCrypto computes the same `S256` — is not here, and since B-34 it is
 * not missing either: the same `commonTest` runs in a real Chrome (`:auth-client:wasmJsBrowserTest`).
 * What runs *here* is the JDK provider, against a live provider.
 *
 * **The two steps in the middle are a person, and this test plays them.** Fetching the sign-in page
 * and posting credentials is what a human does in shildik's own UI; the client under test never sees
 * either, which is exactly why the method is not its business (research §1.6c1).
 *
 * ```bash
 * docker compose -f docker/compose.yaml up -d && bash docker/bootstrap-shildik.sh
 * SHASHKI_SHILDIK=http://127.0.0.1:18081 ./gradlew :auth-client:jvmTest --tests '*Shildik*'
 * ```
 */
class SignInAgainstShildikTest {
    @Test
    fun `an attempt this module built is one shildik completes`() =
        runBlocking {
            val issuer = System.getenv(ISSUER_VARIABLE)
            assumeTrue(!issuer.isNullOrBlank(), "no $ISSUER_VARIABLE: this test needs a running shildik")

            HttpClient(CIO) { followRedirects = false }.use { client ->
                val attempt = SignInAttempt.begin(config(issuer!!))

                // 1. The address this module builds is one the provider serves.
                val page = client.get(attempt.authorizeUrl())
                assertEquals(HttpStatusCode.OK, page.status, "shildik did not recognise the authorize URL")
                val parked = PARKED_STATE.find(page.bodyAsText())?.groupValues?.get(1)
                assertTrue(parked != null, "no parked state on the sign-in page")

                // 2. The person signs in. Same-origin, because shildik serves the form and checks it.
                val redirect =
                    client.submitForm(
                        url = "$issuer/realms/$REALM/oauth2/login/password",
                        formParameters =
                            parameters {
                                append("state", parked)
                                append("login", EMAIL)
                                append("password", PASSWORD)
                            },
                    ) {
                        header(HttpHeaders.Origin, issuer)
                        header(HttpHeaders.Referrer, "$issuer/realms/$REALM/oauth2/authorize")
                    }
                assertEquals(HttpStatusCode.Found, redirect.status, redirect.bodyAsText().take(200))

                val callback = Url(redirect.headers[HttpHeaders.Location]!!)
                val code = callback.parameters["code"]
                assertTrue(!code.isNullOrBlank(), "no code on the callback: $callback")
                assertEquals(attempt.state, callback.parameters["state"], "the state did not come back")

                // 3. The exchange, with the verifier that never left this process.
                val tokens =
                    client.submitForm(
                        url = "$issuer/realms/$REALM/oauth2/token",
                        formParameters =
                            parameters {
                                attempt.tokenForm(code, callback.parameters["state"]!!).forEach { (k, v) ->
                                    append(k, v)
                                }
                            },
                    )
                assertEquals(HttpStatusCode.OK, tokens.status, tokens.bodyAsText().take(300))
                assertTrue("access_token" in tokens.bodyAsText(), "no token in ${tokens.bodyAsText().take(200)}")
            }
        }

    /**
     * B-26's third criterion, against the real thing rather than against a unit test's expectation.
     *
     * A callback whose `state` is not this attempt's is somebody else's callback, and the refusal
     * happens where the code would be spent — not in the caller, which is the place it is forgotten.
     */
    @Test
    fun `a tampered state is refused before the code is ever sent`() =
        runBlocking {
            val issuer = System.getenv(ISSUER_VARIABLE)
            assumeTrue(!issuer.isNullOrBlank(), "no $ISSUER_VARIABLE: this test needs a running shildik")

            val attempt = SignInAttempt.begin(config(issuer!!))

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    attempt.tokenForm(code = "a-real-looking-code", returnedState = "somebody-elses-state")
                }

            assertTrue("different sign-in attempt" in failure.message.orEmpty(), failure.message.orEmpty())
        }

    private fun config(issuer: String) =
        SignInConfig(
            issuer = issuer,
            realm = REALM,
            clientId = CLIENT,
            redirectUri = REDIRECT,
        )

    private companion object {
        const val ISSUER_VARIABLE = "SHASHKI_SHILDIK"
        const val REALM = "shashki"
        const val CLIENT = "rider"
        const val REDIRECT = "http://127.0.0.1:18080/callback"
        const val EMAIL = "rider@example.com"
        const val PASSWORD = "correct-horse-battery-staple"

        /** shildik parks the authorization request and puts its id in the form. */
        val PARKED_STATE = Regex("""name="state"[^>]*value="([^"]+)"""")
    }
}
