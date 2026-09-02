package io.github.youndie.shashki.driver.feature.documents.ui

/**
 * Asking the person for a file (B-47).
 *
 * **A port because a file dialog is the platform's**, and the two platforms this bundle has answer
 * it very differently: a browser opens `<input type="file">` and reads the bytes back through
 * `FileReader`; a desktop window built for goldens has nobody to ask, and says so by answering
 * nothing rather than by throwing.
 *
 * The callback rather than a suspending return: both platforms deliver the file to a listener, and
 * a `suspendCancellableCoroutine` around that would add a cancellation contract this screen has no
 * use for — the person either picks a file or does not.
 */
public expect fun pickDocument(onPicked: (bytes: ByteArray, contentType: String) -> Unit)
