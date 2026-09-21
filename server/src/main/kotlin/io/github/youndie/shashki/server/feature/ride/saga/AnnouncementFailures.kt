package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.petich.AnnouncementFailureHandler
import io.github.youndie.petich.OutboxEvent
import io.github.youndie.petich.Petich
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * What leaves the database when an announcement could not be made (B-97).
 *
 * **The saga completes either way, and that is petich's decision rather than a gap.** By the time an
 * announcement runs the work is done — the ride is assigned, the money has moved — so a member that
 * throws here is counted and stepped over rather than rolled back. Without a handler the count was
 * the only trace: `COMPLETED` saga, correct row, and a consumer at the far end that is never told.
 * Not late. Never.
 *
 * This server had already refused exactly this shape once, for the outbox: `requireOutbox = true`
 * and a `RefusingMetrics` that turns a dropped event into an error. The announcement path was the
 * same drop with no switch on it, and that was not a decision — `AnnouncementFailureHandler` arrived
 * one petich snapshot after the one this build was pinned to (B-96).
 */
internal class AnnouncementFailures(
    private val json: Json,
    private val log: org.slf4j.Logger = LoggerFactory.getLogger("shashki.saga"),
) : AnnouncementFailureHandler {
    override suspend fun failed(
        petich: Petich,
        stepKey: String,
        reason: String,
    ): List<OutboxEvent> {
        val rideId = (petich.payload as? AboutARide)?.rideId
        if (rideId == null) {
            // Cannot happen today and is not swallowed in case it does: both saga payloads are
            // `AboutARide`, and a saga type that is not about a ride has no place to be published
            // to. Emitting one keyed by the saga's own id would put a row in the ride projection
            // under a ride nobody has, which is worse than the loud line this leaves instead.
            log.error(
                "announcement {} of saga {} ({}) failed and its payload does not name a ride; nothing published",
                stepKey,
                petich.id,
                petich.type,
            )
            return emptyList()
        }

        // THE REASON IS LOGGED AND NOT PUBLISHED, deliberately (petich B-57). It is an exception's
        // own message, so it carries whatever threw it: `publish-settled` sends a receipt, and an
        // SMTP failure names the recipient — which is `riderEmail`, sitting in the payload two
        // fields away. The outbox goes to a broker and out to whoever reads the topic; the log stays
        // here. So the event carries this server's own vocabulary and the reason goes where the
        // people who can act on it already look.
        log.error("announcement {} of saga {} could not be made: {}", stepKey, petich.id, reason)

        return listOf(
            RideOutboxEvent(
                // KEYED THE WAY EVERY OTHER EVENT HERE IS KEYED, because the id is a routing key:
                // the consumer takes the ride back out with `substringBeforeLast(':')`. Hence the
                // member's key after a dash rather than a colon — a second colon would file this
                // under a ride that does not exist. See `AboutARide`.
                id = "$rideId:announcement-failed-$stepKey",
                type = SagaAnnouncementFailedEvent.TYPE,
                payload =
                    json.encodeToString(
                        SagaAnnouncementFailedEvent.serializer(),
                        SagaAnnouncementFailedEvent(
                            rideId = rideId,
                            sagaId = petich.id,
                            sagaType = petich.type,
                            member = stepKey,
                        ),
                    ),
            ),
        )
    }
}

/**
 * "The announcement this saga owed you was never made."
 *
 * **It says which announcement rather than what went wrong**, and the difference is the point: the
 * fields below are this server's own vocabulary — a ride id, a saga id, a saga type, a member's
 * declared key — while the reason is an exception's message and belongs to whatever threw it. See
 * [AnnouncementFailures].
 *
 * A reader that knows the saga knows what is missing: `order`/`publish-assigned` means no
 * `ride.assigned`, `settlement`/`publish-settled` means no `ride.settled` and possibly no receipt.
 */
@Serializable
public data class SagaAnnouncementFailedEvent(
    val rideId: String,
    val sagaId: String,
    val sagaType: String,
    val member: String,
) {
    public companion object {
        public const val TYPE: String = "saga.announcement-failed"
    }
}
