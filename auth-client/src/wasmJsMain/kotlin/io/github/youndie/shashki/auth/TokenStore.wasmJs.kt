@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.auth

/**
 * `sessionStorage`, with the cost stated in [TokenStore]'s own note.
 *
 * **`sessionStorage` and not `localStorage`**: the shorter of the two lifetimes is the right default
 * for a bearer token, and a token that survives closing the tab is a token somebody left behind on a
 * shared machine.
 *
 * Reads are wrapped: a browser configured to block site data throws on the *accessor* rather than
 * returning nothing, and an application that fell over there would be an application that cannot be
 * opened in a private window.
 */
private object BrowserTokenStore : TokenStore {
    override fun token(): String? = read(TOKEN)

    override fun keep(token: String) {
        write(TOKEN, token)
    }

    override fun forget() {
        remove(TOKEN)
    }

    override fun parked(): ParkedAttempt? {
        val verifier = read(VERIFIER) ?: return null
        val state = read(STATE) ?: return null
        val nonce = read(NONCE) ?: return null
        return ParkedAttempt(verifier, state, nonce)
    }

    override fun park(attempt: ParkedAttempt) {
        write(VERIFIER, attempt.verifier)
        write(STATE, attempt.state)
        write(NONCE, attempt.nonce)
    }

    override fun unpark() {
        remove(VERIFIER)
        remove(STATE)
        remove(NONCE)
    }

    private const val TOKEN = "shashki.token"
    private const val VERIFIER = "shashki.pkce.verifier"
    private const val STATE = "shashki.pkce.state"
    private const val NONCE = "shashki.pkce.nonce"
}

public actual fun tokenStore(): TokenStore = BrowserTokenStore

private fun read(key: String): String? = readJs(key).takeIf { it.isNotEmpty() }

@JsFun("(key) => { try { return sessionStorage.getItem(key) || '' } catch (e) { return '' } }")
private external fun readJs(key: String): String

@JsFun("(key, value) => { try { sessionStorage.setItem(key, value) } catch (e) {} }")
private external fun write(
    key: String,
    value: String,
)

@JsFun("(key) => { try { sessionStorage.removeItem(key) } catch (e) {} }")
private external fun remove(key: String)

/**
 * `location.assign`, and not `replace`.
 *
 * Assigning leaves the page this application was on in the history, so a person who presses back
 * from the provider's page lands where they started rather than being bounced forward again.
 */
public actual fun redirectTo(url: String): Unit = assign(url)

@JsFun("(url) => window.location.assign(url)")
private external fun assign(url: String)
