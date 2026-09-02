package io.github.youndie.shashki.driver.feature.offer.domain

import io.github.youndie.shashki.driver.UseCase
import io.github.youndie.shashki.driver.suspendRunCatching
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.RideView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The board, polled.
 *
 * **A poll and not a socket, and the socket next to it is why that is not an inconsistency.** The
 * position stream exists because the *client* has something to say every few seconds. An offer is
 * one message in a shift, and pushing it would mean a second connection, a second reconnect policy
 * and a second thing to get wrong for a message that arrives once an hour. The server's own
 * `FindOfferUseCase` says "what the driver's app polls" in as many words.
 *
 * Errors are dropped rather than emitted: a poll that failed is one the next one repeats in two
 * seconds, and a driver does not need to be told about it.
 */
public class WatchOfferUseCase(
    private val offers: OfferRepository,
    private val interval: Duration = 2.seconds,
) {
    public operator fun invoke(driverId: String): Flow<OfferView?> =
        flow {
            while (true) {
                emit(runCatching { offers.forDriver(driverId) }.getOrNull())
                delay(interval)
            }
        }
}

/**
 * How long this offer has left, and how long it had when this client first saw it.
 *
 * **Both numbers come from the server's own clock.** `expiresAtEpochMs - nowEpochMs` is a duration
 * the server measured; subtracting a browser's wall clock from the deadline instead would put a
 * laptop that is an hour out in the position of drawing an offer that expired long ago or one that
 * never starts.
 *
 * [total] is what was left at receipt rather than the server's full budget for the offer, which is
 * not on the wire. The board is polled every couple of seconds, so the difference is small — but it
 * is a difference, and the bar it draws starts full for that reason rather than by accident.
 */
public fun OfferView.remainingAtReceipt(): Duration = (expiresAtEpochMs - nowEpochMs).milliseconds

/**
 * Accept or decline.
 *
 * **The outcome is read from the answer, never assumed from the absence of an exception.** The
 * server refuses an accept that arrived after the cascade moved on — see `OfferGone` — and a client
 * that treated any non-throwing call as success would show a trip belonging to a different driver.
 */
public class AnswerOfferUseCase(
    private val offers: OfferRepository,
) : UseCase<AnswerOfferUseCase.Params, AnswerOutcome> {
    override suspend fun invoke(params: Params): Result<AnswerOutcome> =
        suspendRunCatching {
            try {
                val ride =
                    offers.answer(params.rideId, OfferAnswer(params.driverId, params.decision))
                when (params.decision) {
                    DriverDecision.ACCEPT -> AnswerOutcome.Accepted(ride)
                    DriverDecision.DECLINE -> AnswerOutcome.Declined
                }
            } catch (gone: OfferGone) {
                // Not a failure: a race the driver should be told about in words, not a red error.
                AnswerOutcome.Gone(gone.rideId)
            }
        }

    public class Params(
        public val rideId: String,
        public val driverId: String,
        public val decision: DriverDecision,
    )
}

/** What became of the answer. */
public sealed interface AnswerOutcome {
    public data class Accepted(
        val ride: RideView,
    ) : AnswerOutcome

    public data object Declined : AnswerOutcome

    public data class Gone(
        val rideId: String,
    ) : AnswerOutcome
}
