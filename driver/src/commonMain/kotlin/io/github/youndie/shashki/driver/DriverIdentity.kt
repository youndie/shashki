package io.github.youndie.shashki.driver

import io.github.youndie.shashki.auth.TokenStore
import io.github.youndie.shashki.auth.subjectClaim

/**
 * Who this bundle is, right now (B-53).
 *
 * **Asked rather than held, because the answer changes while the application runs.** The bundle is
 * built before anybody signs in and the identity arrives with the token; a value captured at
 * construction is `SHASHKI_DRIVER_ID` for the life of the page, which is what made every position
 * frame a frame for somebody else.
 */
public fun interface DriverIdentity {
    public fun current(): String
}

/**
 * The token's subject, or the configured id when there is no token.
 *
 * **This is the client half of B-52's rule and it took a running stand to notice it was missing.**
 * The server replaces a claimed id with the subject on every HTTP route, so nothing there ever
 * complained; the socket *compares* instead — relabelling a frame would file another driver's car
 * under this one — and dropped every frame the bundle sent, silently, while the shift screen counted
 * them as delivered. A client that does not claim an identity of its own cannot disagree with the
 * one it was given.
 *
 * [configured] stays, and is not a fallback in the apologetic sense: a server with no provider has no
 * subject to offer, that is the demo everybody runs first, and there the configured id is the only
 * answer there is.
 */
public class TokenDriverIdentity(
    private val tokens: TokenStore,
    private val configured: String,
) : DriverIdentity {
    override fun current(): String = tokens.token()?.let(::subjectClaim) ?: configured
}
