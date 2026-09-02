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
 * **A poll that failed and a board that is empty are different facts** (B-64). This used to emit
 * `runCatching { … }.getOrNull()`, so a body that would not parse, a token that expired mid-shift and
 * a transport error all arrived at the screen as *no offer* — silently, for as long as they lasted.
 * The comment above them said a driver does not need to be told, and that is true of one failed poll
 * and false of every poll failing: the screen then shows a shift in which nothing is happening while
 * the server is waiting for an answer. So the outcome is named, and the screen decides what to say.
 */
public class WatchOfferUseCase(
    private val offers: OfferRepository,
    private val interval: Duration = 2.seconds,
) {
    public operator fun invoke(driverId: String): Flow<Board> =
        flow {
            while (true) {
                // `suspendRunCatching` and not `runCatching`: the ordinary one swallows
                // `CancellationException`, so going offline would look like a failed poll.
                emit(
                    suspendRunCatching { offers.forDriver(driverId) }
                        .fold(
                            onSuccess = { offer -> offer?.let(Board::Offered) ?: Board.Empty },
                            onFailure = { Board.Unreachable(it.message ?: "the board could not be read") },
                        ),
                )
                delay(interval)
            }
        }
}

/** What the last poll of the board found. */
public sealed interface Board {
    /** The server has no offer for this driver. */
    public data object Empty : Board

    public data class Offered(
        val offer: OfferView,
    ) : Board

    /**
     * The board could not be read at all.
     *
     * **Not the same as empty, which is the whole point of this type.** A driver whose client cannot
     * reach the board sees the same screen as a driver nobody is offering anything to, and there is
     * no way to tell them apart from the outside — which is how an offer that arrived at the client
     * and never reached the screen went unnoticed on a running stand.
     */
    public data class Unreachable(
        val message: String,
    ) : Board
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
