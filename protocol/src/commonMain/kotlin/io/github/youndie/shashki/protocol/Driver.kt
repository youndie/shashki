package io.github.youndie.shashki.protocol

import kotlinx.serialization.Serializable

/**
 * What a driver's app sends up the position socket, once a few seconds while online.
 *
 * **`rideClass` and `rating` are self-reported, and that is a seam rather than a design.** There is
 * no driver record yet, so the only place these can come from is the message; a real system reads
 * them server-side from the driver's row and ignores whatever the socket claims. The fields stay
 * where they are when that lands — what changes is who fills them, and the socket stops being
 * believed. Named here because a self-reported rating is the kind of hole that is obvious in a
 * comment and invisible in a schema.
 */
@Serializable
public data class DriverReport(
    val driverId: String,
    val rideClass: RideClass,
    val rating: Double,
    val at: GeoPoint,
)

/** Where the driver's app sends [DriverReport]s. A path, so the client does not spell it either. */
public const val DRIVER_POSITIONS_PATH: String = "/api/driver/positions"
