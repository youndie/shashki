package io.github.youndie.shashki.rider.feature.auth.domain

import io.github.youndie.shashki.auth.SignInAttempt
import io.github.youndie.shashki.auth.SignInConfig
import io.github.youndie.shashki.auth.TokenStore

/**
 * Who this browser is signed in as, and how it becomes signed in.
 *
 * **This is the join B-41 exists for.** `SignInAttempt` was built, tested against a live shildik and
 * proven in a browser; the server verifies what it produces and refuses a forged one. Nothing called
 * either from the application, so with a provider configured every ride route answered 401 — a tier
 * written in three documents and satisfied by no client.
 *
 * **No provider configured is a running configuration**, and it is the one every test and every
 * offline demo uses: [configured] is false, [token] is null, and the server's routes are open
 * because it has no provider either. The two absences have to agree, and they agree by both being
 * read from an environment somebody sets once.
 *
 * There is no view model here and no screen. Signing in is a navigation out of the application and
 * back; what would be on such a screen is a button, and the flow starts by itself when the server
 * says 401.
 */
public class Session(
    private val store: TokenStore,
    private val config: SignInConfig?,
    private val exchange: TokenExchange,
    /**
     * Handing the tab to the provider.
     *
     * **A parameter and not the global `redirectTo`**, and the browser suite is what settled it: with
     * the function called directly, running these tests in a real Chrome navigated karma's own page
     * to shildik in the middle of the run. A test that cannot be run in the place the code runs is a
     * test about something else — so the navigation is injected, and what it was asked to open is a
     * thing an assertion can look at.
     */
    private val redirect: (url: String) -> Unit,
) {
    public val configured: Boolean get() = config != null

    public fun token(): String? = store.token()

    /**
     * Start a sign-in: park the attempt and hand the tab to the provider.
     *
     * **Parking before redirecting, in that order**, because the redirect may take effect before the
     * next line of this function would have run.
     */
    public suspend fun begin() {
        val config = config ?: return
        val attempt = SignInAttempt.begin(config)
        store.park(attempt.parked())
        redirect(attempt.authorizeUrl())
    }

    /**
     * The provider sent the browser back. Returns whether this tab is now signed in.
     *
     * **A callback with no parked attempt is not this tab's**, and neither is one whose `state` does
     * not match — `tokenForm` refuses that itself, which is why the check is not repeated here.
     * Either way the answer is no, and the parked attempt is cleared: a verifier that outlives its
     * exchange is a secret kept for nothing.
     */
    public suspend fun complete(
        code: String,
        state: String,
    ): Boolean {
        val config = config ?: return false
        val parked = store.parked() ?: return false
        return try {
            val attempt = SignInAttempt.resume(config, parked)
            val token = exchange.token(config, attempt.tokenForm(code, state))
            store.keep(token)
            true
        } catch (_: IllegalArgumentException) {
            // Somebody else's callback, or a tampered `state`. Not an error to show a person.
            false
        } finally {
            store.unpark()
        }
    }

    /** The token was refused. Forget it and start again — an expired token is the ordinary case. */
    public suspend fun renew() {
        store.forget()
        begin()
    }
}

/** The code exchange, as a port: the provider's token endpoint is a different service's HTTP. */
public interface TokenExchange {
    public suspend fun token(
        config: SignInConfig,
        form: Map<String, String>,
    ): String
}
