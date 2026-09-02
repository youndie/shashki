@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.ui.nav

/**
 * `history.pushState` out, `popstate` in.
 *
 * **`pushState` and not `replaceState`**: replacing gives an address that can be copied and a back
 * button that leaves the site, which is the half-working version people notice immediately.
 *
 * The listener is called with the address the browser *arrived at*, not with an instruction to pop —
 * forward is a thing too, and a handler that only popped would break it silently.
 */
private object BrowserAddressBar : AddressBar {
    override fun openedAt(): String = currentPath()

    override fun queryAt(): Map<String, String> =
        currentQuery()
            .removePrefix("?")
            .split("&")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val name = pair.substringBefore('=')
                name.decoded() to pair.substringAfter('=', "").decoded()
            }

    override fun push(path: String) {
        if (path != currentPath()) pushPath(path)
    }

    override fun onNavigate(listener: (path: String) -> Unit) {
        listenToPopState { path -> listener(path.toString()) }
    }
}

public actual fun addressBar(): AddressBar = BrowserAddressBar

@JsFun("() => window.location.pathname")
private external fun currentPath(): String

@JsFun("() => window.location.search")
private external fun currentQuery(): String

/** `decodeURIComponent`, because a `state` is base64url and a provider may percent-encode it. */
@JsFun("(s) => { try { return decodeURIComponent(s.replace(/\\+/g, ' ')) } catch (e) { return s } }")
private external fun decodeUriComponent(value: String): String

private fun String.decoded(): String = decodeUriComponent(this)

@JsFun("(path) => window.history.pushState(null, '', path)")
private external fun pushPath(path: String)

@JsFun("(listener) => window.addEventListener('popstate', () => listener(window.location.pathname))")
private external fun listenToPopState(listener: (JsString) -> Unit)
