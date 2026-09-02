@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.rider

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

@JsFun("(path) => window.history.pushState(null, '', path)")
private external fun pushPath(path: String)

@JsFun("(listener) => window.addEventListener('popstate', () => listener(window.location.pathname))")
private external fun listenToPopState(listener: (JsString) -> Unit)
