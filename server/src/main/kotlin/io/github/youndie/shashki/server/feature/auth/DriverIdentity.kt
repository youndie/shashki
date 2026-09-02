package io.github.youndie.shashki.server.feature.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import ru.workinprogress.oidc.OidcPrincipal

/**
 * Which driver is making this request (B-52).
 *
 * **The token wins, and it does not compare — it replaces.** The four driver routes used to take an
 * id the caller chose: a path segment for the offers poll, a body field for the answer and the
 * advance, an id inside every position frame. Anyone who knew a driver's id could read the offer
 * waiting for them, accept it, and drive somebody else's trip to `COMPLETED`, which captures the
 * rider's hold.
 *
 * The item that closed this said why it is a replacement rather than a check: *a route that takes an
 * id the caller chose **and** a token is a route that has to compare them, and a route that has to
 * compare them will one day not.* So when there is a principal, the claimed value is ignored
 * entirely and the subject is the answer; there is no branch in which they can disagree.
 *
 * **The socket is the one exception and it is deliberate** — see `driverPositionRoutes`. A frame
 * carrying somebody else's id is dropped and counted rather than relabelled: silently rewriting the
 * id would file another driver's position under the connected one, which is worse than losing it.
 *
 * With no provider configured there is no principal and [claimed] is the only source there is. That
 * is the demo configuration, it is why this returns a value rather than refusing, and the routes are
 * not behind `authenticate` in that case at all — a 401 would need a provider to be reachable.
 */
public fun ApplicationCall.driverIdentity(claimed: String?): String =
    principal<OidcPrincipal>()?.subject
        ?: claimed
        ?: throw MissingDriverIdentityException()

/** No token and no id: a request that cannot say who it is from. */
public class MissingDriverIdentityException : IllegalArgumentException("no driver identity on this request")
