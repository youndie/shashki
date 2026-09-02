package io.github.youndie.shashki.driver

import io.github.youndie.shashki.auth.InMemoryTokenStore
import io.github.youndie.shashki.auth.TokenStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Who the bundle says it is (B-53).
 *
 * **What this is really testing is that the answer is asked for and not remembered.** The bundle is
 * built before anybody signs in, so a value read once at construction is `SHASHKI_DRIVER_ID` for the
 * life of the page — and the server, after B-52, thinks the driver is the token's subject. The two
 * disagreed on the running stand and the socket dropped every frame, which is the only way anybody
 * found out.
 */
@OptIn(ExperimentalEncodingApi::class)
class DriverIdentityTest {
    @Test
    fun `with no token the configured id is the only answer there is`() {
        val identity = TokenDriverIdentity(InMemoryTokenStore(), configured = "driver-1")

        assertEquals("driver-1", identity.current())
    }

    @Test
    fun `a token replaces it with its subject`() {
        val tokens = InMemoryTokenStore().also { it.keep(tokenFor("someone@example.com")) }

        assertEquals("someone@example.com", TokenDriverIdentity(tokens, "driver-1").current())
    }

    /**
     * **The point of the whole item, as one assertion**: an identity read at construction is the
     * configured id for ever, and the socket drops every frame that carries it. Signing in has to
     * change the answer of an object that already exists.
     */
    @Test
    fun `signing in after the bundle was built changes the answer`() {
        val tokens: TokenStore = InMemoryTokenStore()
        val identity = TokenDriverIdentity(tokens, configured = "driver-1")
        assertEquals("driver-1", identity.current())

        tokens.keep(tokenFor("someone@example.com"))

        assertEquals("someone@example.com", identity.current(), "the identity was captured, not asked")
    }

    /** A token this client cannot read is not a licence to invent one. */
    @Test
    fun `an unreadable token falls back rather than guessing`() {
        val tokens = InMemoryTokenStore().also { it.keep("not.a.jwt") }

        assertEquals("driver-1", TokenDriverIdentity(tokens, "driver-1").current())
    }

    /** Three dots and a base64url payload — the shape, not a signature anybody would accept. */
    private fun tokenFor(subject: String): String {
        val payload =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode("""{"sub":"$subject","email":"$subject"}""".encodeToByteArray())
        return "header.$payload.signature"
    }
}
