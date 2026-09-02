package io.github.youndie.shashki.server.testing

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverCandidate

/**
 * A constant list of candidates, for the tests that are about the *saga* — its phases, its
 * compensations, its cascade — and not about matching.
 *
 * **In the test sources on purpose.** B-20's acceptance says no hand-written candidate list is left
 * in the saga, and the way to hold that is for production to have no such class to bind: the module
 * binds `GeoCandidateSource` over the index and nothing else can be chosen by accident. The saga
 * tests keep it because a cascade over three known drivers is a sharper test of the cascade than a
 * cascade over whoever the simulator happened to park nearby.
 */
class FixedCandidateSource(
    private val candidates: List<DriverCandidate> = DEFAULT,
) : CandidateSource {
    override fun candidates(
        pickup: GeoPoint,
        rideClass: RideClass,
    ): List<DriverCandidate> = candidates

    companion object {
        /**
         * All three in the same place, because these tests are about the cascade and not the map.
         *
         * The saga only reads `driverId`; the position exists for the wait a rider is shown (B-31),
         * which is `PickupEtaTest`'s subject and is measured against a real graph there.
         */
        private val AT = GeoPoint(46.0511, 14.5051)

        val DEFAULT: List<DriverCandidate> =
            listOf(
                DriverCandidate("driver-1", distanceMetres = 800, rating = 4.9, at = AT),
                DriverCandidate("driver-2", distanceMetres = 1_400, rating = 4.7, at = AT),
                DriverCandidate("driver-3", distanceMetres = 2_100, rating = 4.8, at = AT),
            )
    }
}
