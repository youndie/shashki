package io.github.youndie.shashki.crash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * The JVM's own hook, and it keeps whatever was there before.
 *
 * **Replacing an existing handler is how a crash reporter breaks a process it was added to protect**
 * — the previous handler is what prints the stack trace, or what the test framework installed. So
 * this one reports and then delegates.
 *
 * The send is blocking here and not on the browser: the JVM is about to end the thread, so a
 * `launch` would be cancelled before it reached the socket. A short bound keeps a dead network from
 * turning a crash into a hang.
 */
public actual fun installCrashReporting(
    reporter: CrashReporter,
    scope: CoroutineScope,
) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            runBlocking(scope.coroutineContext) {
                kotlinx.coroutines.withTimeout(SEND_TIMEOUT_MS) {
                    reporter.report(throwable, mapOf("thread" to thread.name))
                }
            }
        }
        previous?.uncaughtException(thread, throwable)
    }
}

/** Long enough for a report on a slow link, short enough that a dead one does not hold the exit. */
private const val SEND_TIMEOUT_MS = 3_000L
