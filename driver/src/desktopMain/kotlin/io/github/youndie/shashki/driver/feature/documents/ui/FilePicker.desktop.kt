package io.github.youndie.shashki.driver.feature.documents.ui

/**
 * Nothing, and deliberately not a `TODO()`.
 *
 * The desktop build of this bundle exists so the screens can be photographed and so a local stand
 * can be driven; a file dialog on it would be an AWT window in the middle of a golden. The browser
 * is where a driver uploads a licence, and that actual is the real one.
 */
public actual fun pickDocument(onPicked: (bytes: ByteArray, contentType: String) -> Unit): Unit = Unit
