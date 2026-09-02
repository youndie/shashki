package io.github.youndie.shashki.driver.feature.shift

import io.github.youndie.shashki.driver.feature.offer.domain.OfferGone
import io.github.youndie.shashki.driver.feature.offer.domain.OfferRepository
import io.github.youndie.shashki.driver.feature.shift.domain.ShiftRepository
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.OfferView
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** A socket that takes everything, or one that will not open. */
class FakeShiftRepository : ShiftRepository {
    var failWith: Throwable? = null

    /**
     * Whether the server accepts what is sent (B-54).
     *
     * **A repository that echoes everything it is handed is a server that accepts everything**, and
     * that is the assumption under which the shift's count was written and the assumption the
     * running stand broke: a bundle claiming an id the token contradicted had every frame refused
     * and its screen counted all of them. `false` is that server, and it is a state a test can be in.
     */
    var accepting: Boolean = true

    val sent = mutableListOf<DriverReport>()

    override fun stream(reports: Flow<DriverReport>): Flow<DriverReport> {
        failWith?.let { throw it }
        // Sent is what left; what this flow emits is what came back, which is the distinction the
        // whole item is about.
        return reports.map { it.also(sent::add) }.filter { accepting }
    }
}

/** A board with at most one offer, and an answer the test decides. */
class FakeOfferRepository : OfferRepository {
    var offer: OfferView? = null
    var gone: Boolean = false
    var answered: OfferAnswer? = null

    /** A board this client cannot read — a body that will not parse, a 401, a dead network (B-64). */
    var unreadable: Throwable? = null

    override suspend fun forDriver(driverId: String): OfferView? {
        unreadable?.let { throw it }
        return offer
    }

    override suspend fun answer(
        rideId: String,
        answer: OfferAnswer,
    ): RideView {
        answered = answer
        if (gone) throw OfferGone(rideId)
        return RIDE.copy(id = rideId, driverId = answer.driverId, status = RideStatus.ASSIGNED)
    }

    companion object {
        val PICKUP = GeoPoint(46.0511, 14.5051)
        val DROPOFF = GeoPoint(46.2237, 14.4576)
        val QUOTE = Quote(22_806, 2_079, 2_490, "USD")

        val RIDE =
            RideView(
                id = "ride-1",
                status = RideStatus.MATCHING,
                rideClass = RideClass.ECONOMY,
                pickup = PICKUP,
                dropoff = DROPOFF,
                quote = QUOTE,
            )

        /** An offer with [seconds] left, as the server's own clock measured it. */
        fun offer(
            seconds: Long,
            nowEpochMs: Long = 1_000_000,
            rideId: String = "ride-1",
        ) = OfferView(
            rideId = rideId,
            rideClass = RideClass.ECONOMY,
            quote = QUOTE,
            pickup = PICKUP,
            dropoff = DROPOFF,
            expiresAtEpochMs = nowEpochMs + seconds * 1_000,
            nowEpochMs = nowEpochMs,
        )
    }
}
