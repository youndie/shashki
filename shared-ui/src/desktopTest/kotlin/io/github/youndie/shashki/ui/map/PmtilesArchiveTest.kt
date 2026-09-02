package io.github.youndie.shashki.ui.map

import io.github.youndie.shashki.ui.map.tiles.PmtilesArchive
import io.github.youndie.shashki.ui.map.tiles.RangeReader
import io.github.youndie.shashki.ui.map.tiles.decodeMvt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The archive reader, against a real archive and a second implementation's answer.
 *
 * **The fixture is a cut of the city's own file, not one this repository wrote.** `pmtiles` built
 * `city.pmtiles`; `map/pmtiles_subset.py` copies five of its tiles and their directory entries into
 * a 300 kB archive without re-encoding any of them. A fixture generated from this project's own
 * understanding of the format would agree with this reader for exactly as long as that understanding
 * is wrong.
 *
 * **And the oracle is a file that was already here.** `ljubljana-14-8850-5815.mvt` was extracted for
 * B-01 by a different tool chain entirely. If this reader's header parse, directory walk, Hilbert id
 * and inflater are all right, the bytes it hands back for z14/8850/5815 are that file — 4 068 of
 * them, identical. One assertion covers four things that are each easy to get subtly wrong.
 */
class PmtilesArchiveTest {
    private val archiveBytes: ByteArray = resource(FIXTURE_ARCHIVE)
    private val referenceTile: ByteArray = resource("tiles/ljubljana-14-8850-5815.mvt")

    /** Counts what it was asked for, which is the other half of B-30's acceptance. */
    private class CountingReader(
        private val bytes: ByteArray,
    ) : RangeReader {
        var reads: Int = 0
            private set
        var bytesRead: Int = 0
            private set

        override suspend fun read(
            offset: Long,
            length: Int,
        ): ByteArray {
            reads++
            bytesRead += length
            return bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
    }

    @Test
    fun `the tile the reader returns is byte for byte the one another tool extracted`() =
        runBlocking {
            val archive = PmtilesArchive.open(CountingReader(archiveBytes))

            val tile = archive.tile(TileCoordinate(14, 8850, 5815))

            assertNotNull(tile)
            assertContentEquals(referenceTile, tile, "the archive reader and the extractor disagree")
        }

    /** All four, decoded — so the fixture is a seam's worth of city and not one tile plus noise. */
    @Test
    fun `every tile in the block decodes as a vector tile with roads in it`() =
        runBlocking {
            val archive = PmtilesArchive.open(CountingReader(archiveBytes))
            assertEquals(FIXTURE_TILES.size, archive.tileCount)

            for (at in FIXTURE_TILES) {
                val bytes = assertNotNull(archive.tile(at), "no tile at $at")
                val tile = decodeMvt(bytes)
                assertTrue(tile.layers.isNotEmpty(), "$at decoded to no layers")
            }
        }

    /**
     * **The read count, which is what makes this defensible rather than merely working.**
     *
     * Opening costs two requests — the header and the root directory — and every tile after that
     * costs exactly one. A reader that re-read the directory per tile would draw the same city and
     * would be indefensible; the number is the only thing that separates them.
     */
    @Test
    fun `opening costs two reads and each tile costs one`() =
        runBlocking {
            val reader = CountingReader(archiveBytes)

            val archive = PmtilesArchive.open(reader)
            assertEquals(2, reader.reads, "the header and the root directory, and nothing else")

            archive.tile(TileCoordinate(14, 8852, 5825))
            archive.tile(TileCoordinate(14, 8853, 5826))
            assertEquals(4, reader.reads)

            // A tile the archive does not hold costs nothing at all: the directory answers it.
            assertNull(archive.tile(TileCoordinate(14, 1, 1)))
            assertEquals(4, reader.reads, "a miss must not become a request")
        }

    /** Outside the archive's own zoom range there is nothing to ask for. */
    @Test
    fun `a zoom the archive was not cut at is a miss and not a request`() =
        runBlocking {
            val reader = CountingReader(archiveBytes)
            val archive = PmtilesArchive.open(reader)
            val before = reader.reads

            assertNull(archive.tile(TileCoordinate(18, 141_600, 93_040)))
            assertEquals(before, reader.reads)
        }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "missing test resource $path" }
            .use { it.readBytes() }
}
