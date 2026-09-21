package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.petich.Petich
import org.slf4j.LoggerFactory

/**
 * What the sweeper says about itself, because otherwise it says nothing (B-95).
 *
 * `SuspendedPetichSweeper` takes ten parameters and was built with four. The other six are
 * callbacks, every one of them defaulting to a no-op, so neither what the worker did nor what it
 * failed at reached this application at all.
 *
 * **The failure one is the point.** petich's worker survives a storage failure deliberately — the
 * work is not going anywhere and the next pass picks it up — but surviving is not the same as being
 * visible: a sweeper whose query has been refusing every pass for an hour looks EXACTLY like one
 * with nothing to sweep. petich carries no logger; this application does.
 *
 * **Nothing here may throw.** In the version this build is pinned to, `onWorkerFailure` is called
 * from the `catch` that keeps the worker's loop going, so an exception thrown out of it ends the
 * sweeper for the life of the process — silently, since the only thing that would have said so is
 * this class. Every method below does one logging call and no work.
 */
internal class SweeperReport(
    private val log: org.slf4j.Logger = LoggerFactory.getLogger("shashki.sweeper"),
) {
    /**
     * [stage] is petich's, and it is the useful half: `sweep` for a whole pass that could not run,
     * `expire:<id>` for one saga the deadline could not be applied to, `stuck:<id>` for one the
     * re-drive could not carry on. So the line says which queue and, where there is one, which
     * saga — without this class having to know either.
     *
     * `warn` rather than `error`: one failed pass is the ordinary weather of a background worker,
     * and the thing worth paging on is the RATE, which is what a log this shape lets somebody build.
     */
    fun workerFailure(
        stage: String,
        cause: Throwable,
    ) {
        log.warn("sweeper failed at {}: {}", stage, cause.message ?: cause::class.simpleName)
    }

    /**
     * A saga picked up after the process that was running it died.
     *
     * Normally zero. Non-zero says instances are dying mid-saga, and **nothing else in this system
     * is in a position to notice that** — the saga's row looks like a healthy one, and the request
     * that started it is long gone. B-93 gave the sweeper this job; until now nobody read the
     * answer.
     */
    fun revived(petichId: String) {
        log.warn("re-drove saga {} after the process running it died", petichId)
    }

    /**
     * A row whose saga type this engine has no definition for.
     *
     * `error`, and it is the loudest of the three on purpose: it means a definition was deployed
     * away under sagas that are still in flight. The sweeper skips them rather than ending them,
     * which is right and is also why nothing else will ever mention them again.
     */
    fun unknownType(petich: Petich) {
        log.error("no definition for saga type {} — row {} is being skipped", petich.type, petich.id)
    }
}
