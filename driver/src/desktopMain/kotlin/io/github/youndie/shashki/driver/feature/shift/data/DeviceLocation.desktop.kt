package io.github.youndie.shashki.driver.feature.shift.data

import io.github.youndie.shashki.protocol.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A desktop window has nobody to ask, so it asks nobody.
 *
 * The bundle on the desktop exists to be photographed and to run the graph's tests; a location API
 * for it would be a second implementation of the thing under test with no user behind it.
 */
public actual fun deviceLocation(): Flow<GeoPoint> = emptyFlow()
