package io.github.youndie.shashki.driver.feature.shift.domain

import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One report the socket took, and where the position in it came from. */
public data class Sent(
    val report: DriverReport,
    val source: PositionSource,
)

/**
 * Going online: a report every few seconds, for as long as the flow is collected.
 *
 * **The cadence is this bundle's and not the device's** (B-49). `watchPosition` fires when a phone
 * decides it has something to say — which in a moving car is several times a second and in a parked
 * one is never — and the server's index wants neither. So the fixes go into a cell that keeps the
 * latest, and the ticker reads it: a driver standing still still reports, and a driver on a motorway
 * does not flood the socket.
 *
 * **What is on the wire is identical either way.** A configured point and a device fix are the same
 * `DriverReport`, indexed the same way; the difference exists only on the screen, which is why the
 * source rides back beside the echo rather than in it.
 *
 * The first report goes out immediately: a driver who pressed "go online" and waited four seconds to
 * become a candidate has been told a small lie by the button. That is also why the first fix is the
 * configured point rather than a permission prompt's answer — see [PositionFixes].
 */
public class GoOnlineUseCase(
    private val shift: ShiftRepository,
    private val positions: PositionFixes,
    private val interval: Duration = 4.seconds,
) {
    public operator fun invoke(
        driverId: String,
        rideClass: RideClass,
        rating: Double,
        configured: GeoPoint,
    ): Flow<Sent> =
        channelFlow {
            val current = MutableStateFlow(Fix(configured, PositionSource.CONFIGURED))
            val watching = launch { positions.fixes(configured).collect { current.value = it } }

            val reports =
                flow {
                    while (true) {
                        val fix = current.value
                        emit(DriverReport(driverId = driverId, rideClass = rideClass, rating = rating, at = fix.at))
                        delay(interval)
                    }
                }

            launch {
                // The source is read when the echo arrives rather than carried through the socket:
                // the wire has no field for it and inventing one would put a client's opinion in the
                // protocol. Between a first device fix and the next tick the label can name a source
                // one report ahead of the wire, which is four seconds of a truth arriving early.
                shift.stream(reports).collect { send(Sent(it, current.value.source)) }
                close()
            }

            awaitClose { watching.cancel() }
        }
}
