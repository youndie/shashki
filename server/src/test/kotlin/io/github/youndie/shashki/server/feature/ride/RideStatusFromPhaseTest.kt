package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichPhase
import io.github.youndie.petich.PetichStatus
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.server.feature.ride.data.toRideView
import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one place in either consumer that reads a saga's phase, pinned — petich B-39.
 *
 * `rideStatus()` maps a running saga to what the rider sees, and for DRAFT/PROCESSING it asks the
 * phase. That made it the only thing in shashki or konekt that could notice `hold-payment` moving
 * out of AUTHORIZATION into EXECUTION, and the move does change one reading, so the reading is
 * written down here rather than described.
 *
 * **What moved.** petich commits a row after every member that proceeds and writes nothing when a
 * phase ends, so the phase a reader sees is the one whose first member has already finished. The
 * hold used to be AUTHORIZATION's last member: a process that died holding the money left
 * `(AUTHORIZATION, ...)`, and the rider saw REQUESTED until `offer` — the first EXECUTION member —
 * had run. Now the hold IS the first EXECUTION member, so that same death leaves `(EXECUTION, 1)`
 * and the rider sees MATCHING.
 *
 * **Which is the better reading**, and the reason this is a note and not a new item: the money is
 * held and the ride is committed to, so REQUESTED — "we have your request" — was the lie. Nothing
 * else keys off it: the countdown in `search` is gated on MATCHING *and* on offer data the hold has
 * not written yet, so it stays null exactly as before.
 *
 * The live path never sees either reading. While the saga waits for a driver it is
 * PENDING_SIGNATURE, which maps to MATCHING without consulting the phase at all; this arm is only
 * reached by a reader that catches another process mid-run, or by a row a death left behind.
 */
class RideStatusFromPhaseTest {
    @Test
    fun `a saga parked after the hold reads as matching, where it used to read as requested`() {
        assertEquals(RideStatus.MATCHING, parkedAt(PetichPhase.EXECUTION).toRideView(NOW).status)
        assertEquals(RideStatus.REQUESTED, parkedAt(PetichPhase.AUTHORIZATION).toRideView(NOW).status)
    }

    @Test
    fun `a saga waiting for a driver reads as matching whatever phase it sits in`() {
        val waiting = parkedAt(PetichPhase.VALIDATION).copy(status = PetichStatus.PENDING_SIGNATURE)
        assertEquals(RideStatus.MATCHING, waiting.toRideView(NOW).status)
    }

    @Test
    fun `the countdown stays absent until an offer has written its numbers`() {
        assertEquals(null, parkedAt(PetichPhase.EXECUTION).toRideView(NOW).search)
    }

    private fun parkedAt(phase: PetichPhase): Petich =
        Petich(
            id = "ride-1",
            type = ORDER_SAGA_TYPE,
            status = PetichStatus.PROCESSING,
            currentPhase = phase,
            payload =
                OrderPayload(
                    "ride-1",
                    riderId = "rider-1",
                    pickup = GeoPoint(46.0569, 14.5058),
                    dropoff = GeoPoint(46.2237, 14.4576),
                    rideClass = RideClass.COMFORT,
                    paymentMethodId = "card-4417",
                ),
        )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
