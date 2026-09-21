package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichEngineConfig
import io.github.youndie.petich.PetichResult
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.petichDefinition
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.feature.receipt.data.ExposedReceiptClaims
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.ride.saga.PublishAssigned
import io.github.youndie.shashki.server.feature.ride.saga.SagaAnnouncementFailedEvent
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.feature.settlement.saga.PublishSettled
import io.github.youndie.shashki.server.feature.settlement.saga.SETTLEMENT_SAGA_TYPE
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import io.github.youndie.shashki.server.testing.PostgresHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-97: an announcement that could not be made leaves a row, and the saga still finishes.
 *
 * **Both of this server's announcements start by reading enrichment that has to be there** —
 * `ctx.enriched(Enriched.DRIVER_ID) ?: error(...)` and `ctx.enriched(Settled.CHARGE_AMOUNT) ?:
 * error(...)`. petich does not roll a saga back over that, deliberately: by the time an announcement
 * runs the ride is assigned and the money has moved. So before this the saga ended `COMPLETED`, the
 * row was correct, and `ride.assigned` was never emitted at all.
 *
 * **The definitions below hold the announcement and nothing else, on purpose.** What is being tested
 * is the member that fails and what happens after it, and a full chain would have to be broken
 * somewhere to get there — which would test the breaking rather than the announcement. The member
 * itself is the production one.
 */
class AnnouncementFailureTest {
    private val json = sagaJson()

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the engine needs a clock and this test asserts on the outbox, not on time",
    )
    private val clock = PetichClock { System.currentTimeMillis() }

    private val storage = SagaStorage(PostgresHarness.database, json, clock)

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `an assignment nobody could announce leaves a row and the saga still completes`() =
        runTest {
            val engine =
                sagaEngine(
                    storage,
                    clock,
                    definitions =
                        listOf(
                            petichDefinition<OrderPayload>(ORDER_SAGA_TYPE) {
                                announce("publish-assigned", PublishAssigned(json))
                            },
                        ),
                )

            val result = engine.process(order())

            // The saga finishes, which is petich's decision and not a gap: the work is already done.
            assertIs<PetichResult.Success>(result)
            assertEquals(PetichStatus.COMPLETED, storage.petiches.findById(RIDE)?.status)

            // THE ACCEPTANCE. Before this the outbox was empty here and a counter was the only trace.
            val pending = storage.outbox.fetchPending()
            assertEquals(listOf(SagaAnnouncementFailedEvent.TYPE), pending.map { it.type })
            assertEquals("$RIDE:announcement-failed-publish-assigned", pending.single().id)

            val event = json.decodeFromString(SagaAnnouncementFailedEvent.serializer(), pending.single().payload)
            assertEquals(SagaAnnouncementFailedEvent(RIDE, RIDE, ORDER_SAGA_TYPE, "publish-assigned"), event)
        }

    @Test
    fun `a settlement nobody could announce is keyed by the ride rather than by the saga`() =
        runTest {
            val engine =
                sagaEngine(
                    storage,
                    clock,
                    definitions =
                        listOf(
                            petichDefinition<SettlementPayload>(SETTLEMENT_SAGA_TYPE) {
                                announce(
                                    "publish-settled",
                                    PublishSettled(
                                        json,
                                        SendReceiptUseCase(SilentReceipts()),
                                        ExposedReceiptClaims(PostgresHarness.database, clock),
                                    ),
                                )
                            },
                        ),
                )

            val result = engine.process(settlement())

            assertIs<PetichResult.Success>(result)
            assertEquals(PetichStatus.COMPLETED, storage.petiches.findById(SETTLEMENT_SAGA)?.status)

            // THE HALF THE ORDER SAGA CANNOT SHOW. This saga's id is `<ride>:settlement`, and the
            // consumer takes the ride back out of an event id with `substringBeforeLast(':')` — so
            // an event keyed by `petich.id` would be filed under a ride nobody has. The handler asks
            // the payload, which is what `AboutARide` exists for.
            val pending = storage.outbox.fetchPending()
            assertEquals("$RIDE:announcement-failed-publish-settled", pending.single().id)
            assertEquals(
                RIDE,
                pending
                    .single()
                    .id
                    .substringBeforeLast(':')
                    .substringBefore(':'),
            )

            val event = json.decodeFromString(SagaAnnouncementFailedEvent.serializer(), pending.single().payload)
            assertEquals(SETTLEMENT_SAGA, event.sagaId)
            assertEquals(RIDE, event.rideId)
        }

    @Test
    fun `the published fact carries no exception message`() =
        runTest {
            // petich B-57: `reason` is an exception's own message, and `publish-settled` sends a
            // receipt — an SMTP failure names the recipient, which is `riderEmail`, two fields away
            // in the payload this saga carries. The outbox goes to a broker; the log stays here.
            val engine =
                sagaEngine(
                    storage,
                    clock,
                    definitions =
                        listOf(
                            petichDefinition<OrderPayload>(ORDER_SAGA_TYPE) {
                                announce("publish-assigned", PublishAssigned(json))
                            },
                        ),
                )
            engine.process(order())

            val published =
                storage.outbox
                    .fetchPending()
                    .single()
                    .payload
            assertTrue("reason" !in published, "the exception's message reached the outbox: $published")
            assertTrue(EMAIL !in published, published)
            assertTrue("no driver" !in published, "the announcement's own words reached the outbox: $published")
        }

    @Test
    fun `an engine with no announcement handler is refused at construction`() {
        // The switch beside `requireOutbox`, and this is what holds it wired: without the argument
        // in `sagaEngine` the config below refuses, so the two cannot drift apart silently.
        val failure =
            runCatching {
                io.github.youndie.petich.PetichEngine(
                    repository = storage.petiches,
                    config = PetichEngineConfig(requireAnnouncementFailureHandler = true),
                    clock = clock,
                    definitions = emptyList(),
                )
            }.exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertTrue(failure.message?.contains("no-op") == true, "${failure.message}")
    }

    private class SilentReceipts : ReceiptSender {
        override suspend fun send(receipt: Receipt): Boolean = true
    }

    private fun order() =
        Petich(
            id = RIDE,
            type = ORDER_SAGA_TYPE,
            status = PetichStatus.DRAFT,
            payload =
                OrderPayload(
                    rideId = RIDE,
                    riderId = "rider-1",
                    pickup = GeoPoint(46.0511, 14.5051),
                    dropoff = GeoPoint(46.2237, 14.4576),
                    rideClass = RideClass.ECONOMY,
                    paymentMethodId = "card-4417",
                    riderEmail = EMAIL,
                ),
        )

    private fun settlement() =
        Petich(
            id = SETTLEMENT_SAGA,
            type = SETTLEMENT_SAGA_TYPE,
            status = PetichStatus.DRAFT,
            payload =
                SettlementPayload(
                    rideId = RIDE,
                    riderId = "rider-1",
                    driverId = "driver-1",
                    holdId = "hold-1",
                    quote = Quote(20_500, 1_560, 20_500L, "USD"),
                    rideClass = RideClass.ECONOMY,
                    kind = SettlementPayload.Kind.FARE,
                    riderEmail = EMAIL,
                    pickup = "46.0511, 14.5051",
                    dropoff = "46.2237, 14.4576",
                    paymentMethodId = "card-4417",
                ),
        )

    private companion object {
        const val RIDE = "ride-97"
        const val SETTLEMENT_SAGA = "ride-97:settlement"
        const val EMAIL = "rider@example.test"
    }
}
