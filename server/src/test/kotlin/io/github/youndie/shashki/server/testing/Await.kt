package io.github.youndie.shashki.server.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Waits for something the server reaches on its own schedule.
 *
 * A fixed `delay` in its place is the shape that passes on a fast machine and fails on a loaded
 * one, and the failure then reads as a broken feature rather than a short sleep. The message names
 * what never happened, because "timeout after 10s" names nothing.
 */
suspend fun awaitTrue(
    what: String,
    timeoutMillis: Long = 15_000,
    condition: suspend () -> Boolean,
) {
    runCatching {
        withTimeout(timeoutMillis) {
            while (!condition()) delay(20)
        }
    }.getOrElse { throw AssertionError("never became true within ${timeoutMillis}ms: $what") }
}
