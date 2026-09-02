package io.github.youndie.shashki.driver

import kotlin.coroutines.cancellation.CancellationException

/**
 * One operation, one class, called like a function. The same three lines the server declares.
 *
 * **A third copy of eleven lines, and the same finding as `PortableTypography`'s.** It cannot live
 * in `:protocol` — that module is the wire contract, and a use-case interface is how a client is
 * built rather than what it says. Written down rather than solved, because ending it is a decision
 * about the portfolio.
 */
public interface UseCase<in P, out R> {
    public suspend operator fun invoke(params: P): Result<R>
}

/**
 * `runCatching` that does not swallow cancellation.
 *
 * The plain one turns a cancelled coroutine into `Result.failure`, and a view model then reports "it
 * failed" for a load the user themselves navigated away from — a snackbar on a screen nobody is
 * looking at, and a state machine that thinks it is idle when it is gone.
 */
public inline fun <R> suspendRunCatching(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
