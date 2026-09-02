package io.github.youndie.shashki.driver.feature.shift.domain

import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Going online: a report every few seconds, for as long as the flow is collected.
 *
 * **The position is where the driver was configured to be, and it does not move.** There is no
 * geolocation here: the browser's API needs a permission prompt and a device that is actually going
 * somewhere, and a fabricated drift would be the client inventing data the server then indexes as
 * fact. What the server needs from this bundle is a driver who is *present* and can be a candidate,
 * and a fixed point is honestly that. Movement belongs to `DriverSimulator`, which is the server's
 * own and says what it is.
 */
public class GoOnlineUseCase(
    private val shift: ShiftRepository,
    private val interval: Duration = 4.seconds,
) {
    public operator fun invoke(
        driverId: String,
        rideClass: RideClass,
        rating: Double,
        at: GeoPoint,
    ): Flow<DriverReport> {
        val report = DriverReport(driverId = driverId, rideClass = rideClass, rating = rating, at = at)
        return shift.stream(
            flow {
                // The first report goes out immediately: a driver who pressed "go online" and waited
                // four seconds to become a candidate has been told a small lie by the button.
                while (true) {
                    emit(report)
                    delay(interval)
                }
            },
        )
    }
}
