package io.github.youndie.shashki.server.feature.settlement

import io.github.youndie.petich.EnrichedPayload
import io.github.youndie.petich.OutboxEvent
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichAnnouncement
import io.github.youndie.petich.PetichAnnouncementContext
import io.github.youndie.petich.PetichCheck
import io.github.youndie.petich.PetichCheckContext
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichMemberProbe
import io.github.youndie.petich.PetichPhase
import io.github.youndie.petich.PetichResult
import io.github.youndie.petich.PetichSideEffect
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.PetichStepRecord
import io.github.youndie.petich.petichDefinition
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.billing.ExposedPayoutRepository
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.billing.Payout
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.feature.settlement.saga.CaptureStep
import io.github.youndie.shashki.server.feature.settlement.saga.ChargeAndPayout
import io.github.youndie.shashki.server.feature.settlement.saga.Commission
import io.github.youndie.shashki.server.feature.settlement.saga.PayoutStep
import io.github.youndie.shashki.server.feature.settlement.saga.PublishSettled
import io.github.youndie.shashki.server.feature.settlement.saga.RideSettledEvent
import io.github.youndie.shashki.server.feature.settlement.saga.SETTLEMENT_SAGA_TYPE
import io.github.youndie.shashki.server.feature.settlement.saga.Settleable
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementStep
import io.github.youndie.shashki.server.feature.settlement.saga.settlementPetich
import io.github.youndie.shashki.server.testing.PostgresHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The settlement saga against a real Postgres, and B-37's second criterion made literal: **whichever
 * step the process dies after, no money is left taken and no payout is left standing.**
 *
 * The order saga's own test asks the mirror-image question — no hold *left*, because nothing had
 * been captured yet. Here the side effect being undone is a payment that already moved, and the
 * compensation is a refund rather than a release. That difference is the reason `PaymentGateway`
 * grew a fourth method rather than reusing the third.
 */
class SettlementSagaTest {
    private val json = sagaJson()
    private val storage = SagaStorage(PostgresHarness.database, json)
    private val payments = InMemoryPaymentGateway()
    private val payouts = ExposedPayoutRepository(PostgresHarness.database) { 0L }

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the engine needs a clock and this test asserts on money moved, not on time",
    )
    private val clock = PetichClock { System.currentTimeMillis() }
    private val receipts = RecordingReceipts()

    // THE PRODUCTION FACTORY, not a hand-built copy of it. A list assembled here would be a second
    // declaration of the saga's order, and the one that drifts is the one nobody deploys.
    private fun definition(sender: ReceiptSender = receipts): PetichDefinition<SettlementPayload> =
        settlementPetich(payments, payouts, SendReceiptUseCase(sender), json)

    private fun engine(definition: PetichDefinition<SettlementPayload>) =
        sagaEngine(storage, clock, definitions = listOf(definition))

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a fare runs every phase, takes the money once, records the payout and leaves one event`() =
        runTest {
            val hold = payments.hold("fixture-1", "card-4417", FARE, "USD")
            val result = engine(definition()).process(settlement(hold, SettlementPayload.Kind.FARE))

            assertIs<PetichResult.Success>(result)
            assertEquals(emptyList(), payments.activeHolds().toList(), "the hold outlived the settlement")
            assertEquals(FARE, payments.captured().single().amountCents)
            assertEquals(FARE * 80 / 100, assertNotNull(payouts.find(RIDE)).amountCents)
            assertEquals(listOf(RideSettledEvent.TYPE), storage.outbox.fetchPending().map { it.type })

            val event =
                json.decodeFromString(
                    RideSettledEvent.serializer(),
                    storage.outbox
                        .fetchPending()
                        .single()
                        .payload,
                )
            assertEquals("FARE", event.kind)
            assertEquals(FARE, event.chargedCents)
        }

    /**
     * **The same five phases and one different number**, which is research §1.4c's "same word, two
     * mechanisms" reduced to what actually differs.
     */
    @Test
    fun `a fee runs the same phases and takes a quarter`() =
        runTest {
            val hold = payments.hold("fixture-2", "card-4417", FARE, "USD")
            val result = engine(definition()).process(settlement(hold, SettlementPayload.Kind.FEE))

            assertIs<PetichResult.Success>(result)
            val fee = FARE * Commission.DEFAULT.cancellationPercent / 100
            assertEquals(fee, payments.captured().single().amountCents)
            assertEquals(fee * 80 / 100, assertNotNull(payouts.find(RIDE)).amountCents)
            // The rest of the hold is not taken and not held: a rider who cancelled owes the fee.
            assertEquals(emptyList(), payments.activeHolds().toList())
        }

/**
     * **A tip is the same five phases and a different kind of money** (B-44).
     *
     * There is no hold behind it — the fare's was captured when the trip ended — so AUTHORIZATION
     * charges the card instead, EXECUTION writes a *second* payout row for the ride, and the driver
     * keeps all of it: a platform cut of a tip is a policy this product would be teaching if it
     * invented one.
     */
    @Test
    fun `a tip charges the card, pays the driver in full and leaves the fare alone`() =
        runTest {
            val result =
                engine(definition())
                    .process(settlement(HoldId("no-hold"), SettlementPayload.Kind.TIP, tip = TIP))

            assertIs<PetichResult.Success>(result)
            assertEquals(listOf(TIP), payments.captured().map { it.amountCents }, "the tip was not charged")
            assertEquals(TIP, assertNotNull(payouts.find(RIDE, Payout.TIP)).amountCents)
            assertNull(payouts.find(RIDE), "a tip wrote itself over the fare's payout")
        }

    /**
     * **The B-11 shape, for the money that has no hold** (B-44): a process that dies between the
     * charge and the payout row must leave neither half standing. The compensation refunds the
     * *charge* — the id the step left behind — and not the payload's hold, which for a tip is the
     * fare the rider was happy with.
     */
    @Test
    fun `a tip that dies before its payout gives the money back`() =
        runTest {
            val engine = engine(definitionDying(at = "payout"))

            val result =
                engine.process(
                    settlement(HoldId("no-hold"), SettlementPayload.Kind.TIP, id = "tip-death", tip = TIP),
                )

            assertTrue(result !is PetichResult.Success)
            assertEquals(emptyList(), payments.captured().toList(), "the tip stayed charged")
            assertNull(payouts.find(RIDE, Payout.TIP), "a payout survived the death")
        }

    /**
     * **The same undo, for the tip whose charge never came back** (youndie/shashki#13).
     *
     * The test above is this one's positive control: when `charge` returned, `CHARGE_ID` names what
     * to give back and the tip's own money is refunded. Here the call threw — a gateway timeout, a
     * reset connection — so the step never returned and nothing recorded `CHARGE_ID`. What the
     * compensation must **not** do then is fall back to `payload.holdId`, because
     * `SettleRideUseCase` gives every kind the ride's own hold and for a tip that hold is the fare,
     * already captured. Refunding it gives the rider back the ride they were happy with, and
     * `InMemoryPaymentGateway.refund` finds that hold in `captured` and removes it without a word.
     *
     * **`compensate` is called here rather than reached through the engine, and that is the point.**
     * petich 0.1.0 begins a rollback one step *below* the failure, so the step that threw is never
     * asked to undo itself and this cannot happen today. petich 0.3.0 calls it (youndie/petich#59),
     * because an engine cannot tell an effect that reached the far side from a call that never
     * landed. Making that call by hand is what lets the guard exist before the upgrade instead of
     * after it.
     */
    @Test
    fun `a tip whose charge never landed does not refund the fare`() =
        runTest {
            val fare = payments.hold("fixture-3", "card-4417", FARE, "USD")
            payments.capture(fare, FARE)

            // The saga as it stands when `charge` throws: the tip's payload carries the ride's hold,
            // and nothing has enriched CHARGE_ID.
            val saga = settlement(fare, SettlementPayload.Kind.TIP, id = "tip-no-charge", tip = TIP)
            // THE CONTEXT PETICH SHIPS, not a double of it — which matters here more than
            // anywhere: the case IS that nothing was recorded, and a hand-written context that only
            // remembered what happened in front of it would answer null for a record the saga
            // carries and pass while production failed.
            CaptureStep(
                payments,
            ).compensate(PetichMemberProbe(saga, stepKey = "capture"), saga.payload as SettlementPayload)

            assertEquals(
                listOf(FARE),
                payments.captured().map { it.amountCents },
                "the tip's rollback gave back the fare",
            )
        }

    /** B-37's second and third criteria, at every boundary. */
    @Test
    fun `dying after any phase leaves no money taken and no payout standing`() =
        runTest {
            // **`publish-settled` is not in this list, and used to be.** A death in the receipt
            // member gave the fare back and deleted the payout — a settlement undone because the
            // sentence announcing it could not be built. petich B-41 made an announcement a member
            // that cannot do that; the case below is the same death with the opposite assertion.
            for (dieBefore in listOf("settleable", "capture", "payout")) {
                PostgresHarness.truncateAll()
                val hold = payments.hold("fixture-4", "card-4417", FARE, "USD")
                val engine = engine(definitionDying(at = dieBefore))

                val result = engine.process(settlement(hold, SettlementPayload.Kind.FARE, id = "s-$dieBefore"))

                assertTrue(result !is PetichResult.Success, "$dieBefore: the settlement must not complete")
                assertEquals(emptyList(), payments.captured().toList(), "$dieBefore: money stayed taken")
                assertNull(payouts.find(RIDE), "$dieBefore: a payout survived the death")
                assertEquals(
                    emptyList(),
                    storage.outbox.fetchPending(),
                    "$dieBefore: an event escaped a settlement that never completed",
                )
                payments.activeHolds().forEach { payments.release(it.id) }
            }
        }

    /**
     * The other half of the list above: **money is not given back because a receipt member died**
     * (petich B-41).
     *
     * This is the stronger form of `a receipt that cannot be sent does not roll the settlement back`
     * below. That one is the member surviving a failure it catches by hand; this one is the member
     * not surviving at all, and the settlement standing anyway — which used to be a full refund and
     * a deleted payout, decided by whatever threw inside a notification.
     */
    @Test
    fun `a settlement whose announcement dies keeps the money where it moved it`() =
        runTest {
            PostgresHarness.truncateAll()
            val hold = payments.hold("fixture-5", "card-4417", FARE, "USD")

            val result =
                engine(definitionDying(at = "publish-settled"))
                    .process(settlement(hold, SettlementPayload.Kind.FARE, id = "s-announcement-dies"))

            assertIs<PetichResult.Success>(result)
            assertEquals(FARE, payments.captured().single().amountCents, "the fare was refunded over a notification")
            assertNotNull(payouts.find(RIDE), "the payout was deleted over a notification")
            assertEquals(emptyList(), storage.outbox.fetchPending(), "the event is the only thing missing")
        }

    /**
     * **"Captured exactly once" lives in the gateway, and this is where that is asserted.**
     *
     * A process that dies after the money moves and before the row is written leaves a `PROCESSING`
     * row parked at the next phase. A second process picks it up and continues — it does not re-run
     * AUTHORIZATION. And if something ever did, the gateway refuses: a second capture of a hold that
     * is gone throws rather than charging twice, which is the difference between a bug that is found
     * and a bug that is a bank statement.
     */

    @Test
    fun `a settlement the first process abandoned is finished by the next one, and takes nothing more`() =
        runTest {
            val hold = payments.hold("fixture-6", "card-4417", FARE, "USD")
            payments.capture(hold, FARE)

            val parked =
                settlement(hold, SettlementPayload.Kind.FARE).copy(
                    status = PetichStatus.PROCESSING,
                    currentPhase = PetichPhase.EXECUTION,
                    // The capture used to be an AUTHORIZATION member and this read 0. What keeps it
                    // from running twice was never the phase boundary — petich writes nothing when a
                    // phase ends and commits `index + 1` after every member that proceeds — so the
                    // row a death leaves names the member. EXECUTION is [capture, payout]; 1 is
                    // "the money already moved". The assertion below checks it against the gateway
                    // too, so this number cannot quietly become the only thing under test.
                    currentInterceptorIndex = 1,
                    enrichedPayload = enrichedFor(FARE),
                )
            storage.petiches.saveOrGet(parked)

            val resumed =
                engine(definition()).process(checkNotNull(storage.petiches.findById(RIDE_SAGA)))

            assertIs<PetichResult.Success>(resumed)
            assertEquals(1, payments.captured().size, "the money moved a second time")
            assertEquals(FARE, payments.captured().single().amountCents)
            assertNotNull(payouts.find(RIDE))

            // And the direct refusal, so the guarantee is not only a property of petich's bookkeeping.
            assertFailsWith<IllegalStateException> { payments.capture(hold, FARE) }
        }

    /** B-37's fourth criterion: the receipt is sent by the saga, with what was actually charged. */
    @Test
    fun `the receipt carries the ride and the amount that was taken`() =
        runTest {
            val hold = payments.hold("fixture-7", "card-4417", FARE, "USD")
            engine(definition()).process(settlement(hold, SettlementPayload.Kind.FEE))

            val receipt = receipts.sent.single()
            assertEquals(RIDE, receipt.rideId)
            assertEquals(EMAIL, receipt.to)
            assertEquals(FARE * Commission.DEFAULT.cancellationPercent / 100, receipt.quote.amountCents)
        }

    /**
     * And the other half of it: **a mail server that is down does not undo a payment.**
     *
     * `SendReceiptUseCase`'s KDoc decides this — "a settlement that rolled back because a mail server
     * was down would be the tail wagging the dog" — and the assertion is that the money and the
     * payout are exactly where a successful send would have left them.
     */
    @Test
    fun `a receipt that cannot be sent does not roll the settlement back`() =
        runTest {
            val refusing =
                object : ReceiptSender {
                    override suspend fun send(receipt: Receipt): Boolean = error("the relay refused the connection")
                }
            val hold = payments.hold("fixture-8", "card-4417", FARE, "USD")

            val result =
                engine(definition(refusing)).process(settlement(hold, SettlementPayload.Kind.FARE))

            assertIs<PetichResult.Success>(result)
            assertEquals(FARE, payments.captured().single().amountCents)
            assertNotNull(payouts.find(RIDE), "a mail failure removed the payout")
            assertEquals(listOf(RideSettledEvent.TYPE), storage.outbox.fetchPending().map { it.type })
        }

    private class RecordingReceipts : ReceiptSender {
        val sent = mutableListOf<Receipt>()

        override suspend fun send(receipt: Receipt): Boolean {
            sent += receipt
            return true
        }
    }

    private fun settlement(
        hold: HoldId,
        kind: SettlementPayload.Kind,
        id: String = RIDE_SAGA,
        tip: Long = 0,
    ) = Petich(
        id = id,
        type = SETTLEMENT_SAGA_TYPE,
        status = PetichStatus.DRAFT,
        payload =
            SettlementPayload(
                rideId = RIDE,
                riderId = "rider-1",
                driverId = "driver-1",
                holdId = hold.value,
                quote = Quote(20_500, 1_560, FARE, "USD"),
                rideClass = RideClass.ECONOMY,
                kind = kind,
                riderEmail = EMAIL,
                pickup = "46.0511, 14.5051",
                dropoff = "46.2237, 14.4576",
                paymentMethodId = "card-4417",
                tipCents = tip,
            ),
    )

    /** What ENRICHMENT would have left, for the abandoned-process case that starts after it. */
    private fun enrichedFor(charge: Long) =
        io.github.youndie.petich.SimpleEnrichedPayload(
            mapOf(
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.CHARGE_AMOUNT to charge.toString(),
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.PAYOUT_AMOUNT to
                    (charge * 80 / 100).toString(),
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.CURRENCY to "USD",
            ),
        )

    /**
     * A death is the member never returning, which is what an unplugged process looks like.
     *
     * **Addressed by key rather than by phase**, which is what the model changed: a member's phase
     * is the definition's layout and its key is its identity. The substitution is written out rather
     * than mapped over a list, because the order is a declaration now and a test that rebuilt it
     * from a filter would be asserting against its own copy.
     */
    private fun definitionDying(at: String): PetichDefinition<SettlementPayload> =
        petichDefinition(SETTLEMENT_SAGA_TYPE) {
            enrich("charge-and-payout", ChargeAndPayout())
            if (at == "settleable") validate(at, DyingCheck(at)) else validate("settleable", Settleable())
            if (at == "capture") step(at, Dying(at)) else step("capture", CaptureStep(payments))
            if (at == "payout") step(at, Dying(at)) else step("payout", PayoutStep(payouts))
            if (at == "publish-settled") {
                announce(at, DyingAnnouncement(at))
            } else {
                announce("publish-settled", PublishSettled(json, SendReceiptUseCase(receipts)))
            }
        }

    /** The same death, in the one member petich will not roll back over (B-41). */
    private class DyingAnnouncement(
        private val key: String,
    ) : PetichAnnouncement<SettlementPayload> {
        override suspend fun announce(
            ctx: PetichAnnouncementContext,
            payload: SettlementPayload,
        ): Unit = error("process died before $key answered")
    }

    private class Dying(
        private val key: String,
    ) : PetichStep<SettlementPayload> {
        override suspend fun execute(
            ctx: PetichStepContext,
            payload: SettlementPayload,
        ): Unit = error("process died before $key answered")

        override suspend fun compensate(
            ctx: PetichStepContext,
            payload: SettlementPayload,
        ) = Unit
    }

    private class DyingCheck(
        private val key: String,
    ) : PetichCheck<SettlementPayload> {
        override suspend fun check(
            ctx: PetichCheckContext,
            payload: SettlementPayload,
        ): Unit = error("process died before $key answered")
    }

    private companion object {
        const val RIDE = "ride-1"
        const val RIDE_SAGA = "ride-1:settlement"
        const val EMAIL = "rider@example.com"

        /** What the kit's R8 offers as its middle button. */
        const val TIP = 500L
        const val FARE = 2_690L
    }
}
