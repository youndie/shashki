package io.github.youndie.shashki.crash

import kotlinx.coroutines.CoroutineScope

/**
 * Send whatever the platform lets escape.
 *
 * **The two platforms hand over different things and the signature admits it.** A JVM handler is
 * given a `Throwable`; a browser's `onerror` is given a message and, if it is lucky, a stack string
 * — `CrashReporter` therefore has an overload for each rather than one that fakes a `Throwable` out
 * of two strings.
 *
 * [scope] is where the send runs, because both hooks are synchronous and the send is not. A crash
 * handler that blocked would turn a crash into a hang.
 */
public expect fun installCrashReporting(
    reporter: CrashReporter,
    scope: CoroutineScope,
)
