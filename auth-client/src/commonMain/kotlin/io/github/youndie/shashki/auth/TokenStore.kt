package io.github.youndie.shashki.auth

/**
 * Where the token and the attempt in flight live, across a page that goes away.
 *
 * **The redirect is the whole reason this exists.** Signing in leaves the application, and the
 * verifier — the secret half of PKCE — has to be there when the browser comes back. In-memory state
 * does not survive that, so something has to write it down.
 *
 * **What it costs is written here rather than discovered.** In a browser this is `sessionStorage`:
 * scoped to the tab, cleared when it closes, and **readable by any script running on the origin**. A
 * bearer token there is a bearer token an injected script can take. The alternatives are worse or
 * unavailable for a page like this one — a cookie the application cannot read is a session this
 * server does not have, and keeping the token only in memory means signing in again on every
 * refresh, which for a demo whose whole point is the redirect is not a trade at all.
 *
 * So: `sessionStorage`, the shorter-lived of the two, and the parked attempt is **deleted the moment
 * it is spent**. A verifier that outlives its exchange is a secret kept for nothing.
 */
public interface TokenStore {
    public fun token(): String?

    public fun keep(token: String)

    public fun forget()

    /** The attempt that is out at the provider, or `null` when none is. */
    public fun parked(): ParkedAttempt?

    public fun park(attempt: ParkedAttempt)

    public fun unpark()
}

/**
 * No storage at all: the desktop build, and any test that does not want one.
 *
 * It is not a stub that pretends — a window has no redirect to survive, because the desktop build
 * exists so the browser application can be looked at without a browser (B-02) and nothing in it
 * signs in.
 */
public class InMemoryTokenStore : TokenStore {
    private var token: String? = null
    private var parked: ParkedAttempt? = null

    override fun token(): String? = token

    override fun keep(token: String) {
        this.token = token
    }

    override fun forget() {
        token = null
    }

    override fun parked(): ParkedAttempt? = parked

    override fun park(attempt: ParkedAttempt) {
        parked = attempt
    }

    override fun unpark() {
        parked = null
    }
}

/** The browser's, or [InMemoryTokenStore] where there is no browser. */
public expect fun tokenStore(): TokenStore

/**
 * Send the browser somewhere else — out of this application and into the provider's page.
 *
 * **A separate port from the address bar, because it is the opposite operation.** `AddressBar` keeps
 * a URL in step with a back stack this application owns; this hands the tab to somebody else and
 * does not come back until they redirect. On a target with no browser it does nothing, which is the
 * truth: the desktop build exists to be photographed and nothing in it signs in.
 */
public expect fun redirectTo(url: String)
