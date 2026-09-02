package io.github.youndie.shashki.ui.nav

/**
 * The browser's address bar and its back button, as a port.
 *
 * **In a browser these are the interface, not decoration.** People press back and they paste links,
 * whether or not an application planned for it — and a Compose application that keeps its navigation
 * only in a `SnapshotStateList` answers both by doing nothing, which reads as a broken page rather
 * than as a missing feature. Navigation 3 owns the stack; keeping the address in step with it is
 * this.
 *
 * A port rather than `expect fun` on the call site so the desktop target has something to bind that
 * is honestly nothing — see [NoAddressBar].
 *
 * **It lives here rather than in one bundle because two bind it.** It was written for the rider and
 * moved when the driver needed the same thing (B-29) — which is the test of whether it was a port or
 * that application's own arrangement. Nothing in it changed on the way.
 */
public interface AddressBar {
    /** Where the application was opened. `/` when there is no such notion. */
    public fun openedAt(): String

    /**
     * The query string of the current address, parsed.
     *
     * **A route is a path and a redirect answers with a query**, which is the one thing a
     * `@Serializable` navigation key cannot carry: the provider chooses those parameters, including
     * an `error` it may send instead of a `code`. Empty where there is no address bar.
     */
    public fun queryAt(): Map<String, String>

    /** Put [path] in the bar as a new entry, so the back button has somewhere to go. */
    public fun push(path: String)

    /** The person pressed back or forward. The listener is called with the address they arrived at. */
    public fun onNavigate(listener: (path: String) -> Unit)
}

/**
 * What a desktop window has: no address, no history, and no pretence of either.
 *
 * It is not a `TODO()` and not an empty `object` picked up by accident — the desktop build exists so
 * this application can be photographed by viddik, and a stub that threw would make the screenshot a
 * test of the stub.
 */
public object NoAddressBar : AddressBar {
    override fun openedAt(): String = "/"

    override fun queryAt(): Map<String, String> = emptyMap()

    override fun push(path: String): Unit = Unit

    override fun onNavigate(listener: (path: String) -> Unit): Unit = Unit
}

/** The browser's, or [NoAddressBar] where there is no browser. */
public expect fun addressBar(): AddressBar
