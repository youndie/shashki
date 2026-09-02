package io.github.youndie.shashki.rider.feature.auth

import io.github.youndie.shashki.auth.InMemoryTokenStore
import io.github.youndie.shashki.auth.SignInConfig
import io.github.youndie.shashki.rider.feature.auth.domain.Session
import io.github.youndie.shashki.rider.feature.auth.domain.TokenExchange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The join B-41 exists for, with the redirect taken out of it.
 *
 * `redirectTo` is a no-op on this target, which is what makes these tests possible at all: what they
 * check is the state either side of the navigation — what is parked before the browser leaves, and
 * what is kept and cleared when it comes back.
 */
class SessionTest {
    private val store = InMemoryTokenStore()
    private val exchange = RecordingExchange()
    private var sentTo: String? = null

    @Test
    fun `beginning a sign-in parks an attempt`() =
        runTest {
            session().begin()

            val parked = assertNotNull(store.parked(), "nothing was parked, so the redirect loses the verifier")
            assertTrue(parked.verifier.length >= 43, "the verifier is shorter than RFC 7636 allows")
            assertTrue(parked.state.isNotBlank())

            // And the address the browser would be sent to carries the challenge and this state —
            // never the verifier, which is the whole of PKCE.
            val url = assertNotNull(sentTo, "the browser was not sent anywhere")
            assertTrue("code_challenge=" in url && "code_challenge_method=S256" in url, url)
            assertTrue("state=${parked.state}" in url, url)
            assertFalse(parked.verifier in url, "the verifier went out through the address bar")
        }

    @Test
    fun `a callback exchanges the code and keeps the token`() =
        runTest {
            val session = session()
            session.begin()
            val state = assertNotNull(store.parked()).state

            assertTrue(session.complete(code = "a-code", state = state))

            assertEquals("a-token", session.token())
            assertEquals("a-code", exchange.form?.get("code"))
            // The verifier is in the exchange and nowhere else afterwards.
            assertNotNull(exchange.form?.get("code_verifier"))
            assertNull(store.parked(), "a verifier that outlives its exchange is a secret kept for nothing")
        }

    /**
     * **A tampered `state` is refused and the code is never sent.** `tokenForm` does the refusing —
     * the check belongs where the code would be spent — and this is what says the session honours it
     * rather than catching and retrying.
     */
    @Test
    fun `a callback with somebody else's state signs nobody in`() =
        runTest {
            val session = session()
            session.begin()

            assertFalse(session.complete(code = "a-code", state = "somebody-elses-state"))

            assertNull(session.token())
            assertNull(exchange.form, "the code was sent despite the state not matching")
            assertNull(store.parked())
        }

    /** A callback that arrives with nothing parked is not this tab's. */
    @Test
    fun `a callback with no attempt in flight does nothing`() =
        runTest {
            assertFalse(session().complete(code = "a-code", state = "any"))
            assertNull(exchange.form)
        }

    /**
     * B-41's third criterion: an expired token is the ordinary case, so a 401 renews rather than
     * reporting. The old token must be gone before the redirect, or the tab comes back and sends it
     * again.
     */
    @Test
    fun `renewing forgets the old token and parks a new attempt`() =
        runTest {
            val session = session()
            store.keep("stale")

            session.renew()

            assertNull(session.token())
            assertNotNull(store.parked())
        }

    /** No provider configured is a running configuration, and it does nothing rather than failing. */
    @Test
    fun `with no provider there is no sign-in and no token`() =
        runTest {
            val session = Session(store, config = null, exchange = exchange, redirect = { sentTo = it })

            session.begin()

            assertFalse(session.configured)
            assertNull(store.parked())
            assertNull(session.token())
            assertNull(sentTo, "a browser with no provider was sent somewhere")
        }

    private fun session() = Session(store, CONFIG, exchange, redirect = { sentTo = it })

    private class RecordingExchange : TokenExchange {
        var form: Map<String, String>? = null

        override suspend fun token(
            config: SignInConfig,
            form: Map<String, String>,
        ): String {
            this.form = form
            return "a-token"
        }
    }

    private companion object {
        val CONFIG =
            SignInConfig(
                issuer = "https://id.example",
                realm = "shashki",
                clientId = "rider",
                redirectUri = "https://app.example/callback",
            )
    }
}
