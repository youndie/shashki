package io.github.youndie.shashki.server.feature.settlement

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.billing.ExposedPayoutRepository
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.feature.settlement.saga.CaptureStep
import io.github.youndie.shashki.server.feature.settlement.saga.ChargeAndPayoutStep
import io.github.youndie.shashki.server.feature.settlement.saga.Commission
import io.github.youndie.shashki.server.feature.settlement.saga.PayoutStep
import io.github.youndie.shashki.server.feature.settlement.saga.PublishSettledStep
import io.github.youndie.shashki.server.feature.settlement.saga.RideSettledEvent
import io.github.youndie.shashki.server.feature.settlement.saga.SETTLEMENT_SAGA_TYPE
import io.github.youndie.shashki.server.feature.settlement.saga.SettleableStep
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementStep
import io.github.youndie.shashki.server.testing.PostgresHarness
import kotlinx.coroutines.test.runTest
import ru.workinprogress.petich.InterceptorResult
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPhase
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    private val clock = PetichClock { System.currentTimeMillis() }
    private val receipts = RecordingReceipts()

    private fun steps(sender: ReceiptSender = receipts): List<PetichInterceptor<*>> =
        listOf(
            ChargeAndPayoutStep(),
            SettleableStep(),
            CaptureStep(payments),
            PayoutStep(payouts),
            PublishSettledStep(json, SendReceiptUseCase(sender)),
        )

    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `a fare runs every phase, takes the money once, records the payout and leaves one event`() =
        runTest {
            val hold = payments.hold("card-4417", FARE, "USD")
            val result = sagaEngine(steps(), storage, clock).process(settlement(hold, SettlementPayload.Kind.FARE))

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
            val hold = payments.hold("card-4417", FARE, "USD")
            val result = sagaEngine(steps(), storage, clock).process(settlement(hold, SettlementPayload.Kind.FEE))

            assertIs<PetichResult.Success>(result)
            val fee = FARE * Commission.DEFAULT.cancellationPercent / 100
            assertEquals(fee, payments.captured().single().amountCents)
            assertEquals(fee * 80 / 100, assertNotNull(payouts.find(RIDE)).amountCents)
            // The rest of the hold is not taken and not held: a rider who cancelled owes the fee.
            assertEquals(emptyList(), payments.activeHolds().toList())
        }

    /** B-37's second and third criteria, at every boundary. */
    @Test
    fun `dying after any phase leaves no money taken and no payout standing`() =
        runTest {
            for (dieBefore in listOf(
                PetichPhase.VALIDATION,
                PetichPhase.AUTHORIZATION,
                PetichPhase.EXECUTION,
                PetichPhase.POST_PROCESSING,
            )) {
                PostgresHarness.truncateAll()
                val hold = payments.hold("card-4417", FARE, "USD")
                val engine = sagaEngine(steps().withDeathAt(dieBefore), storage, clock)

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
            val hold = payments.hold("card-4417", FARE, "USD")
            payments.capture(hold, FARE)

            val parked =
                settlement(hold, SettlementPayload.Kind.FARE).copy(
                    status = PetichStatus.PROCESSING,
                    currentPhase = PetichPhase.EXECUTION,
                    currentInterceptorIndex = 0,
                    enrichedPayload = enrichedFor(FARE),
                )
            storage.petiches.saveOrGet(parked)

            val resumed =
                sagaEngine(
                    steps(),
                    storage,
                    clock,
                ).process(checkNotNull(storage.petiches.findById(RIDE_SAGA)))

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
            val hold = payments.hold("card-4417", FARE, "USD")
            sagaEngine(steps(), storage, clock).process(settlement(hold, SettlementPayload.Kind.FEE))

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
            val hold = payments.hold("card-4417", FARE, "USD")

            val result =
                sagaEngine(
                    steps(refusing),
                    storage,
                    clock,
                ).process(settlement(hold, SettlementPayload.Kind.FARE))

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
            ),
    )

    /** What ENRICHMENT would have left, for the abandoned-process case that starts after it. */
    private fun enrichedFor(charge: Long) =
        ru.workinprogress.petich.SimpleEnrichedPayload(
            mapOf(
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.CHARGE_AMOUNT to charge.toString(),
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.PAYOUT_AMOUNT to
                    (charge * 80 / 100).toString(),
                io.github.youndie.shashki.server.feature.settlement.saga.Settled.CURRENCY to "USD",
            ),
        )

    /** A death is the next step never returning, which is what an unplugged process looks like. */
    private fun List<PetichInterceptor<*>>.withDeathAt(phase: PetichPhase): List<PetichInterceptor<*>> =
        map { step ->
            if (step.phase == phase) {
                object : SettlementStep() {
                    override val phase = phase

                    override suspend fun run(
                        petich: Petich,
                        payload: SettlementPayload,
                    ): InterceptorResult = error("process died before $phase answered")
                }
            } else {
                step
            }
        }

    private companion object {
        const val RIDE = "ride-1"
        const val RIDE_SAGA = "ride-1:settlement"
        const val EMAIL = "rider@example.com"
        const val FARE = 2_690L
    }
}
