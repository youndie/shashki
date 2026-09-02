package io.github.youndie.shashki.driver.feature.shift.domain

import io.github.youndie.shashki.protocol.DriverReport
import kotlinx.coroutines.flow.Flow

/**
 * The driver's shift: one socket, held open for as long as they are online.
 *
 * **This is the one place the two clients are not the same shape.** The rider polls a ride and each
 * request either answers or fails on its own; a driver holds a connection, and being online *is* the
 * connection being up. So the contract is a flow whose lifetime is the socket's rather than a
 * suspend function that returns a value.
 */
public interface ShiftRepository {
    /**
     * Opens the position socket, sends what [reports] emits, and emits each report the socket took.
     *
     * Completing means the socket closed — which the server reads as the driver going offline.
     * Throwing means it could not be opened, and the screen has to say so: a driver who thinks they
     * are online and is not will sit there for an hour.
     */
    public fun stream(reports: Flow<DriverReport>): Flow<DriverReport>
}
