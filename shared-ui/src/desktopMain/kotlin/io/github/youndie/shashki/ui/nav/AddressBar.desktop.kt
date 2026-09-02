package io.github.youndie.shashki.ui.nav

/** A window has no address bar, and saying so is the whole implementation. */
public actual fun addressBar(): AddressBar = NoAddressBar
