package io.github.youndie.shashki.server.feature.driver.domain

import io.github.youndie.shashki.protocol.RideClass

/**
 * A driver, as the server knows them (B-63).
 *
 * **The record this product spent five stages without.** Four documents named the same absence in
 * their own words: the class and the rating on a position frame were the driver's own claim, the
 * assigned-ride card had a registration slot that stayed blank, and R8 asked a rider to rate an
 * e-mail address. This is the row those four were missing.
 *
 * There is no registration flow, so rows are seeded — see `V4__drivers.sql`, which says so in its
 * own comment. A driver the server has never heard of is not a candidate: the failure is a driver
 * who cannot go online rather than one who quietly drives a class nobody gave them.
 */
public data class Driver(
    val id: String,
    val name: String,
    val car: String,
    val plate: String,
    val rideClass: RideClass,
)

/**
 * One method, and a `fun interface` on purpose: a test about the simulator's geometry needs a driver
 * to exist and nothing else about them, and writing that should cost one line rather than a class.
 */
public fun interface DriverRepository {
    public fun find(id: String): Driver?
}
