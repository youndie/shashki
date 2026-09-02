package io.github.youndie.shashki.auth

/** A window has no redirect to survive, and saying so is the whole implementation. */
public actual fun tokenStore(): TokenStore = InMemoryTokenStore()

/** Nothing to redirect. The desktop build has no provider to be sent to. */
public actual fun redirectTo(url: String): Unit = Unit
