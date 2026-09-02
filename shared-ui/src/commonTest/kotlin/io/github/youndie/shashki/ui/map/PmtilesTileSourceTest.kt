package io.github.youndie.shashki.ui.map

import io.github.youndie.shashki.ui.map.tiles.PmtilesTileSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An archive that is not there (B-56).
 *
 * **This is the failure that painted a bundle black.** `SHASHKI_TILES_URL` named an object the
 * store did not hold, the range read threw `expected 206 … got 404`, and it threw out of the
 * `LaunchedEffect` that was fetching — which takes the composition with it. The map is the one part
 * of this product that can be missing without the screen being wrong, and the type's own note said
 * so while the code did the opposite.
 */
class PmtilesTileSourceTest {
    @Test
    fun `an archive that cannot be opened is a fact rather than a throw`() =
        runTest {
            var opened = 0
            val source =
                PmtilesTileSource {
                    opened++
                    error("expected 206 for bytes=0-16383 from http://nowhere/city.pmtiles, got 404 Not Found")
                }

            source.load(TileCoordinate(zoom = 14, x = 8, y = 5))

            assertNotNull(source.unavailable, "a missing archive was not reported")
            assertNull(source.loaded(TileCoordinate(zoom = 14, x = 8, y = 5)))
            assertEquals(1, opened)
        }

    /**
     * **Once, not once per tile per frame.** A viewport asks for four tiles and a pan asks again;
     * an archive that is not there would otherwise be a failed range request for every one of them,
     * for as long as the screen is open.
     */
    @Test
    fun `it stops trying`() =
        runTest {
            var opened = 0
            val source =
                PmtilesTileSource {
                    opened++
                    error("no archive")
                }

            repeat(6) { step -> source.load(TileCoordinate(zoom = 14, x = 8 + step, y = 5)) }

            assertEquals(1, opened, "the source went back to an archive it already knows is not there")
        }
}
