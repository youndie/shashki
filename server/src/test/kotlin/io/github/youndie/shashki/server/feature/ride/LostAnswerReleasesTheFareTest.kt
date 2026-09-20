package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichMemberProbe
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.billing.Hold
import io.github.youndie.shashki.server.billing.HoldId
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.HoldPaymentStep
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-91: the gateway committed and the answer never came back.
 *
 * This is the one case petich calls `compensate` for — it cannot tell an effect that reached the far
 * side from a call that never landed — and it is the case the old undo could not act in: the hold id
 * arrives IN the answer that was lost, so `ctx.enriched(HOLD_ID)` was null and the rider's fare
 * stayed held for ever.
 *
 * **A test where the call never landed proves nothing here.** There the id is absent AND the hold is
 * absent, so a rollback that does nothing is right by accident. Only a gateway that commits and then
 * goes quiet separates the two, which is why the fake below can be told to do exactly that.
 */
class LostAnswerReleasesTheFareTest {
    /**
     * Deduplicates by the caller's key and cancels by the id it generated — which is what a real
     * provider offers, and what makes "cancel whatever is under this key" impossible here.
     *
     * [swallowNextAnswer] commits the hold and then throws, which is a lost answer as the caller
     * sees it: indistinguishable from a call that never arrived.
     */
    private class LosingGateway : PaymentGateway {
        private val holds = LinkedHashMap<HoldId, Hold>()
        private val captured = LinkedHashMap<HoldId, Hold>()
        private val byKey = mutableMapOf<String, HoldId>()
        private var next = 0
        var swallowNextAnswer: Boolean = false
        var forgetKeys: Boolean = false

        override fun hold(
            key: String,
            paymentMethodId: String,
            amountCents: Long,
            currency: String,
        ): HoldId {
            if (!forgetKeys) byKey[key]?.let { return it }
            val id = HoldId("hold-${++next}")
            holds[id] = Hold(id, paymentMethodId, amountCents, currency)
            byKey[key] = id
            if (swallowNextAnswer) {
                swallowNextAnswer = false
                error("the gateway never answered")
            }
            return id
        }

        override fun release(hold: HoldId) {
            holds.remove(hold)
        }

        override fun capture(
            hold: HoldId,
            amountCents: Long,
        ) {
            holds.remove(hold)?.let { captured[hold] = it.copy(amountCents = amountCents) }
        }

        override fun charge(
            key: String,
            paymentMethodId: String,
            amountCents: Long,
            currency: String,
        ): HoldId {
            byKey[key]?.let { return it }
            val id = HoldId("charge-${++next}")
            captured[id] = Hold(id, paymentMethodId, amountCents, currency)
            byKey[key] = id
            return id
        }

        override fun refund(hold: HoldId) {
            captured.remove(hold)
        }

        override fun activeHolds(): Collection<Hold> = holds.values.toList()

        override fun captured(): Collection<Hold> = captured.values.toList()
    }

    @Test
    fun `a hold whose answer was lost is released by the rollback`() =
        runTest {
            val gateway = LosingGateway()
            val member = HoldPaymentStep(gateway)
            val probe = PetichMemberProbe(quoted(), stepKey = "hold-payment")

            gateway.swallowNextAnswer = true
            val thrown = runCatchingTheLostAnswer { member.execute(probe, payload()) }
            assertTrue(thrown, "the fixture must stage a lost answer rather than a clean run")
            assertEquals(1, gateway.activeHolds().size, "the gateway committed, which is the premise")
            assertEquals(null, probe.enrichment, "and the member never got to write the id down")

            member.compensate(probe, payload())

            assertEquals(emptyList(), gateway.activeHolds().toList(), "the fare was never released")
        }

    @Test
    fun `the rollback does not take a second hold when the first one landed`() =
        runTest {
            val gateway = LosingGateway()
            val member = HoldPaymentStep(gateway)
            val probe = PetichMemberProbe(quoted(), stepKey = "hold-payment")

            gateway.swallowNextAnswer = true
            runCatchingTheLostAnswer { member.execute(probe, payload()) }
            member.compensate(probe, payload())

            // The replay is answered by the first call, so `hold-2` never exists. Asserting on the
            // count alone would pass even if a second were taken and released.
            assertEquals(emptyList(), gateway.activeHolds().toList())
            assertEquals(emptyList(), gateway.captured().toList())
        }

    @Test
    fun `a call that never landed is still a rollback that does nothing`() =
        runTest {
            val gateway = LosingGateway()
            val member = HoldPaymentStep(gateway)
            val probe = PetichMemberProbe(quoted(), stepKey = "hold-payment")

            // The older rule this must not weaken: `release` has to tolerate arriving without its
            // `hold`. The replay creates one and the release removes it — net zero, same as before.
            member.compensate(probe, payload())

            assertEquals(emptyList(), gateway.activeHolds().toList())
        }

    @Test
    fun `a gateway that forgot the key leaves the first hold standing`() =
        runTest {
            val gateway = LosingGateway()
            val member = HoldPaymentStep(gateway)
            val probe = PetichMemberProbe(quoted(), stepKey = "hold-payment")

            gateway.swallowNextAnswer = true
            runCatchingTheLostAnswer { member.execute(probe, payload()) }
            // THE RETENTION WINDOW CLOSING, kept as a live case rather than a sentence in a KDoc.
            // This is what the inequality beside `KEY_RETENTION` exists to prevent.
            gateway.forgetKeys = true
            member.compensate(probe, payload())

            assertEquals(1, gateway.activeHolds().size, "the replay took a second hold and freed that one")
        }

    /** Staged rather than swallowed: the throw IS the fixture, and a clean run would mean no test. */
    private inline fun runCatchingTheLostAnswer(block: () -> Unit): Boolean =
        try {
            block()
            false
        } catch (expected: IllegalStateException) {
            checkNotNull(expected.message).isNotBlank()
        }

    private fun payload() =
        OrderPayload(
            "ride-1",
            riderId = "rider-1",
            pickup = GeoPoint(46.0569, 14.5058),
            dropoff = GeoPoint(46.2237, 14.4576),
            rideClass = RideClass.COMFORT,
            paymentMethodId = "card-4417",
        )

    /** The saga as it stands when the hold runs: the quote is enriched and nothing else is. */
    private fun quoted() =
        Petich(
            id = "ride-1",
            type = "order",
            status = PetichStatus.PROCESSING,
            payload = payload(),
            enrichedPayload =
                SimpleEnrichedPayload(
                    mapOf(
                        Enriched.QUOTE_DISTANCE to "20500",
                        Enriched.QUOTE_DURATION to "1560",
                        Enriched.QUOTE_AMOUNT to "1000",
                        Enriched.QUOTE_CURRENCY to "USD",
                    ),
                ),
        )
}
