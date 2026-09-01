package io.github.youndie.shashki.server.common

import kotlin.coroutines.cancellation.CancellationException

/** One operation, one class, called like a function: `useCase(Params(…))`. */
public interface UseCase<in P, out R> {
    public suspend operator fun invoke(params: P): Result<R>
}

/**
 * `runCatching` that does not swallow cancellation. The plain one turns a cancelled coroutine into
 * `Result.failure`, and the caller then carries on as if the work had merely failed — which is how a
 * request that was cancelled goes on to write to the database.
 */
public inline fun <R> suspendRunCatching(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
