package io.github.youndie.shashki.ui.map

import androidx.compose.runtime.Immutable
import io.github.youndie.shashki.protocol.GeoPoint

/**
 * Everything this product ever asks a map to show, and nothing else.
 *
 * **Deliberately smaller than any map library's API**, because the point of the seam is that the
 * four routes of B-01 differ behind it and not in front. What the kit draws is a basemap, a route
 * in two phases, cars, and two pins; a scene that could express a heatmap would be a scene that
 * ties the choice of renderer to the screens.
 */
@Immutable
public data class MapScene(
    val camera: MapCamera,
    val route: RouteLine? = null,
    val cars: List<CarMarker> = emptyList(),
    val pins: List<MapPin> = emptyList(),
)

/** Where the map is looking. Zoom is the web-mercator convention every tile scheme uses. */
@Immutable
public data class MapCamera(
    val centre: GeoPoint,
    val zoom: Double = 14.0,
)

/**
 * The route, in the two phases the styles paint differently: what the car has driven and what is
 * left. The style documents carry them as one GeoJSON source filtered on a `phase` property, which
 * is why they are two lists here rather than one line and a progress fraction.
 */
@Immutable
public data class RouteLine(
    val travelled: List<GeoPoint>,
    val ahead: List<GeoPoint>,
)

/**
 * A car on the map.
 *
 * `bearing` turns the car and **not the marker**: the kit's rule is that markers do not rotate with
 * the map, so a renderer that rotates the whole layer is drawing something else. `self` is the
 * driver's own car in the driver application, which the styles tint with the accent.
 */
@Immutable
public data class CarMarker(
    val id: String,
    val at: GeoPoint,
    val bearingDegrees: Float = 0f,
    val self: Boolean = false,
)

/** Pickup or dropoff — the two glyphs `ShashkiIcons` already carries. */
@Immutable
public data class MapPin(
    val at: GeoPoint,
    val kind: Kind,
) {
    public enum class Kind { PICKUP, DROPOFF }
}
