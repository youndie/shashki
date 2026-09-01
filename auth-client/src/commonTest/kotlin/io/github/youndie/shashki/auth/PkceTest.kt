package io.github.youndie.shashki.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PkceTest {
    /**
     * **RFC 7636 Appendix B, verbatim.** The expectation comes from the specification rather than
     * from running this code, which is the whole point: a hash-and-encode written from memory
     * produces confidently wrong output that agrees with itself, and a test whose numbers came out
     * of the implementation would agree with it too.
     */
    @Test
    fun `the challenge is the RFC's own worked example`() =
        runTest {
            val verifier = CodeVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

            val challenge = Pkce.challenge(verifier)

            assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge.value)
            assertEquals("S256", challenge.method)
        }

    /** Padding is not part of it: `=` is outside the unreserved set and would travel differently. */
    @Test
    fun `neither the verifier nor the challenge carries base64 padding`() =
        runTest {
            val verifier = Pkce.newVerifier()

            assertFalse('=' in verifier.value, "a padded verifier: ${verifier.value}")
            assertFalse('=' in Pkce.challenge(verifier).value)
            assertFalse('+' in verifier.value || '/' in verifier.value, "this is base64, not base64url")
        }

    /** RFC 7636 §4.1, and 32 bytes of randomness is exactly the shortest legal string. */
    @Test
    fun `a generated verifier is 43 characters and a different one every time`() {
        val generated = List(64) { Pkce.newVerifier().value }

        assertTrue(generated.all { it.length == 43 }, "lengths: ${generated.map { it.length }.toSet()}")
        assertEquals(64, generated.toSet().size, "the generator repeated itself")
        assertFailsWith<IllegalArgumentException> { CodeVerifier("too-short") }
        assertFailsWith<IllegalArgumentException> { CodeVerifier("x".repeat(129)) }
    }

    /**
     * The acceptance criterion, made literal: **the verifier never leaves the client.** Not "is not
     * put in the URL by this version of the code" — the whole address is searched for it.
     */
    @Test
    fun `the redirect carries the challenge and the token request carries the verifier`() =
        runTest {
            val attempt = SignInAttempt.begin(config)
            val url = attempt.authorizeUrl()
            val form = attempt.tokenForm(code = "the-code", returnedState = attempt.state)
            val verifier = form.getValue("code_verifier")

            assertFalse(verifier in url, "the verifier is in the address bar")
            assertContains(url, "code_challenge=${attempt.challenge.value}")
            assertContains(url, "code_challenge_method=S256")
            assertFalse("code_challenge_method=plain" in url)
            assertFalse(url.contains("code_verifier"), "the verifier's parameter name is in the redirect")
            assertFalse(attempt.challenge.value in form.values, "the challenge is in the token request")

            // And the pair is a real one rather than two unrelated strings.
            assertEquals(attempt.challenge.value, Pkce.challenge(CodeVerifier(verifier)).value)
        }

    /** `plain` is not rejected at run time here — there is no way to express it. */
    @Test
    fun `S256 is the only method this module can produce`() =
        runTest {
            assertEquals("S256", Pkce.challenge(Pkce.newVerifier()).method)
            assertEquals("S256", Pkce.METHOD_S256)
        }

    /** A callback that is not this attempt's is refused where the code is spent, not by the caller. */
    @Test
    fun `a callback whose state does not match is refused`() =
        runTest {
            val attempt = SignInAttempt.begin(config)

            assertFailsWith<IllegalArgumentException> {
                attempt.tokenForm(code = "the-code", returnedState = "somebody-elses-state")
            }
        }

    /** Two attempts share nothing: a verifier reused across sign-ins is a verifier worth stealing. */
    @Test
    fun `two attempts share no secret`() =
        runTest {
            val first = SignInAttempt.begin(config)
            val second = SignInAttempt.begin(config)

            assertTrue(first.state != second.state)
            assertTrue(first.nonce != second.nonce)
            assertTrue(first.challenge.value != second.challenge.value)
        }

    /**
     * The parameter names and the path are shildik's, read out of its `OidcRoutes` and its
     * `OAuth2` resource rather than out of the RFC — a provider that spelled one of them
     * differently would fail only when someone tried to sign in.
     */
    @Test
    fun `the address is the one shildik routes`() =
        runTest {
            val url = SignInAttempt.begin(config).authorizeUrl()

            assertTrue(url.startsWith("https://id.example/realms/shashki/oauth2/authorize?"), url)
            for (parameter in listOf("response_type=code", "client_id=rider", "scope=openid", "state=", "nonce=")) {
                assertContains(url, parameter)
            }
            // The redirect URI's own separators survive encoding rather than splitting the query.
            assertContains(url, "redirect_uri=https%3A%2F%2Fshashki.example%2Fcallback")
        }

    private val config =
        SignInConfig(
            issuer = "https://id.example/",
            realm = "shashki",
            clientId = "rider",
            redirectUri = "https://shashki.example/callback",
        )
}
