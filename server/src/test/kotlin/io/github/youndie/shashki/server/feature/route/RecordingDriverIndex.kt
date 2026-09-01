package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.dispatch.DriverCandidate
import io.github.youndie.shashki.server.dispatch.DriverIndex

/**
 * The index, plus a note of every position that passed through it.
 *
 * `DriverCandidate` carries a distance and a rating and not a point, which is right — a candidate is
 * an answer to "who is near", not a tracker. So a test that wants the track taps the port the
 * reports arrive on rather than widening the type that the production code reads.
 */
internal class RecordingDriverIndex(
    private val delegate: DriverIndex,
    private val seen: MutableList<GeoPoint>,
) : DriverIndex by delegate {
    override fun report(
        report: DriverReport,
        nowEpochMs: Long,
    ) {
        synchronized(seen) { seen += report.at }
        delegate.report(report, nowEpochMs)
    }

    // `by delegate` would cover these; they are here only because Kotlin needs the class to be
    // concrete and the compiler is happier saying so than inferring it across a default argument.
    override fun near(
        point: GeoPoint,
        rideClass: RideClass,
        nowEpochMs: Long,
        limit: Int,
    ): List<DriverCandidate> = delegate.near(point, rideClass, nowEpochMs, limit)
}
