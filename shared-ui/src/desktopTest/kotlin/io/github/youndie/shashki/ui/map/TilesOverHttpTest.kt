package io.github.youndie.shashki.ui.map

import io.github.youndie.shashki.ui.map.tiles.HttpRangeReader
import io.github.youndie.shashki.ui.map.tiles.PmtilesArchive
import io.github.youndie.shashki.ui.map.tiles.PmtilesTileSource
import io.github.youndie.shashki.ui.map.tiles.decodeMvt
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The archive, read the way the product reads it: ranged HTTP against a running object store.
 *
 * **This is the criterion B-30 could not meet from a byte array.** `PmtilesArchiveTest` proves the
 * reader; what it cannot prove is that the transport under it does what the format needs — that the
 * store answers 206 and honours the exact range, and that a 17 MB archive costs two requests plus
 * one per tile rather than a download. The city is 810 tiles and the point of the whole design is
 * that a browser holds it without fetching it.
 *
 * ```bash
 * docker compose -f docker/compose.yaml up -d bochka
 * bash docker/upload-tiles.sh ~/shashki-city/city.pmtiles
 * SHASHKI_TILES=http://127.0.0.1:19000/tiles/city.pmtiles ./gradlew :shared-ui:desktopTest --tests '*OverHttp*'
 * ```
 */
class TilesOverHttpTest {
    @Test
    fun `the city's own archive is read over ranges and the tiles decode`() =
        runBlocking {
            val url = System.getenv(TILES_VARIABLE)
            assumeTrue(!url.isNullOrBlank(), "no $TILES_VARIABLE: this test needs an archive on a store")

            HttpClient(CIO).use { client ->
                val archive = PmtilesArchive.open(HttpRangeReader(client, url!!))

                // The whole city, from two requests. 810 is B-06's count and B-07 measured the
                // reads; if this number moves, the archive being served is not the one those
                // measurements are about.
                assertEquals(810, archive.tileCount)
                assertEquals(0, archive.minZoom)
                assertEquals(14, archive.maxZoom)

                val bytes = assertNotNull(archive.tile(TileCoordinate(14, 8852, 5825)), "no city-centre tile")
                val tile = decodeMvt(bytes)
                assertTrue(
                    tile.layer("transportation")?.features?.isNotEmpty() == true,
                    "the tile came back over HTTP with no roads in it",
                )
            }
        }

    /**
     * **The count, over the real transport.** A viewport is four tiles; a source that went back to
     * the store on every recomposition would draw the same city at sixty ranged reads a second.
     */
    @Test
    fun `a viewport costs one read per tile and a second look costs none`() =
        runBlocking {
            val url = System.getenv(TILES_VARIABLE)
            assumeTrue(!url.isNullOrBlank(), "no $TILES_VARIABLE: this test needs an archive on a store")

            HttpClient(CIO).use { client ->
                val source = PmtilesTileSource { PmtilesArchive.open(HttpRangeReader(client, url!!)) }
                val viewport = MapViewport(MapCamera(SEAM, zoom = 14.0), width = 390f, height = 390f, tileSide = 512f)
                val wanted = viewport.tiles()
                assertEquals(4, wanted.size, "a pane on a corner touches four tiles")

                for (at in wanted) source.load(at)
                assertEquals(4, source.misses)
                assertTrue(wanted.all { source.loaded(it) != null }, "a tile of the city came back empty")

                for (at in wanted) source.load(at)
                assertEquals(4, source.misses, "the second look went back to the store")
            }
        }

    private companion object {
        const val TILES_VARIABLE = "SHASHKI_TILES"

        /** The corner where the four city-centre tiles meet — the same point the seam golden uses. */
        val SEAM =
            io.github.youndie.shashki.protocol
                .GeoPoint(46.04273565, 14.52392578)
    }
}
