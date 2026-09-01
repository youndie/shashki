package io.github.youndie.shashki.server.common

import io.github.youndie.shashki.protocol.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Metres between two points on the sphere. One implementation, because two would drift. */
public fun haversineMetres(
    a: GeoPoint,
    b: GeoPoint,
): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METRES * asin(sqrt(h))
}

private const val EARTH_RADIUS_METRES = 6_371_000.0
