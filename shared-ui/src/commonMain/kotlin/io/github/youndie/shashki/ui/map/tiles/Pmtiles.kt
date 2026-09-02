package io.github.youndie.shashki.ui.map.tiles

import io.github.youndie.shashki.ui.map.TileCoordinate

/**
 * Where the archive's bytes come from — a ranged HTTP read, a file, or a byte array in a test.
 *
 * **A port, because the measurement and the product are different transports.** B-07 measured
 * ranged reads against bochka with a Python client on the standard library; the browser does the
 * same reads with `fetch`. What this project needs to be sure of is the *archive reader*, and that
 * is the same code either way.
 */
public interface RangeReader {
    /** [length] bytes from [offset]. Must return exactly that many or throw. */
    public suspend fun read(
        offset: Long,
        length: Int,
    ): ByteArray
}

/**
 * A pmtiles v3 archive, read through ranges.
 *
 * **Two requests before any tile**: the 127-byte header, then the root directory. Everything after
 * that is one request per tile, and the archive is `clustered`, so the entries a viewport wants sit
 * next to each other in the file. That shape is what B-07 measured — 812 ranged reads at p99 under
 * 3.2 ms — and it is the reason a browser can hold a whole city without downloading one.
 *
 * **Leaf directories are refused rather than half-handled.** The city archive has none (its header
 * says leaf length 0), so an implementation here would be code no test could reach; a reader that
 * silently returned "no tile" for an archive it could not walk would look exactly like a city with
 * no streets in it. B-01 recorded the same fact about the format and this is where it is enforced.
 */
public class PmtilesArchive private constructor(
    private val reader: RangeReader,
    private val dataOffset: Long,
    private val entries: List<Entry>,
    public val minZoom: Int,
    public val maxZoom: Int,
) {
    /** One directory entry. Offsets are relative to the archive's tile-data section. */
    private data class Entry(
        val id: Long,
        val runLength: Int,
        val offset: Long,
        val length: Int,
    )

    /** The tiles this archive holds, for a test that wants to count what it asked for. */
    public val tileCount: Int get() = entries.size

    /**
     * The tile's bytes, decompressed, or `null` when the archive does not hold it.
     *
     * `null` is the ordinary answer at the edge of a city extract, not a failure: a viewport near
     * the boundary straddles tiles that were never cut.
     */
    public suspend fun tile(coordinate: TileCoordinate): ByteArray? {
        if (coordinate.zoom < minZoom || coordinate.zoom > maxZoom) return null
        val id = tileId(coordinate)
        val entry = entries.firstOrNull { it.id <= id && id < it.id + it.runLength } ?: return null
        return gunzip(reader.read(dataOffset + entry.offset, entry.length))
    }

    public companion object {
        /** Reads the header and the root directory: the two requests every archive costs up front. */
        public suspend fun open(reader: RangeReader): PmtilesArchive {
            val header = reader.read(0, HEADER_LENGTH)
            require(header.size >= HEADER_LENGTH) { "a pmtiles header is $HEADER_LENGTH bytes" }
            require(
                MAGIC.indices.all { header[it] == MAGIC[it] } && header[MAGIC.size].toInt() == VERSION,
            ) { "not a pmtiles v3 archive" }

            val rootOffset = header.u64(ROOT_OFFSET)
            val rootLength = header.u64(ROOT_OFFSET + LONG).toInt()
            val leafLength = header.u64(LEAF_OFFSET + LONG)
            require(leafLength == 0L) { "this archive has leaf directories and this reader does not" }

            val directory = gunzip(reader.read(rootOffset, rootLength))
            return PmtilesArchive(
                reader = reader,
                dataOffset = header.u64(DATA_OFFSET),
                entries = directoryEntries(directory),
                minZoom = header[MIN_ZOOM].toInt(),
                maxZoom = header[MAX_ZOOM].toInt(),
            )
        }

        /**
         * The tile id: the zoom's own base, plus the Hilbert index of the tile within that zoom.
         *
         * **Hilbert and not row-major, and that is the whole reason ranged reads work.** Tiles that
         * are near each other on the ground get ids that are near each other in the file, so a
         * viewport's four tiles are one region of the archive rather than four scattered ones.
         */
        internal fun tileId(coordinate: TileCoordinate): Long {
            var base = 0L
            for (z in 0 until coordinate.zoom) base += 1L shl (2 * z)
            var x = coordinate.x.toLong()
            var y = coordinate.y.toLong()
            var index = 0L
            var side = 1L shl (coordinate.zoom - 1)
            while (side > 0) {
                val rx = if (x and side > 0) 1L else 0L
                val ry = if (y and side > 0) 1L else 0L
                index += side * side * ((3 * rx) xor ry)
                if (ry == 0L) {
                    if (rx == 1L) {
                        x = side - 1 - x
                        y = side - 1 - y
                    }
                    val swap = x
                    x = y
                    y = swap
                }
                side /= 2
            }
            return base + index
        }

        /**
         * A v3 directory: four runs of varints, in four passes, over the same entries.
         *
         * The last pass is the one with a rule in it — a zero offset means "immediately after the
         * previous entry", which is how a clustered archive avoids repeating a number it can derive.
         */
        private fun directoryEntries(buffer: ByteArray): List<Entry> {
            var at = 0

            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val byte = buffer[at++].toInt() and BYTE_MASK
                    result = result or ((byte and VARINT_PAYLOAD).toLong() shl shift)
                    if (byte and VARINT_MORE == 0) return result
                    shift += VARINT_BITS
                }
            }

            val count = varint().toInt()
            val ids = LongArray(count)
            var previous = 0L
            for (i in 0 until count) {
                previous += varint()
                ids[i] = previous
            }
            val runs = IntArray(count) { varint().toInt() }
            val lengths = IntArray(count) { varint().toInt() }
            val offsets = LongArray(count)
            for (i in 0 until count) {
                val value = varint()
                offsets[i] = if (value == 0L && i > 0) offsets[i - 1] + lengths[i - 1] else value - 1
            }
            return List(count) { Entry(ids[it], runs[it], offsets[it], lengths[it]) }
        }

        private fun ByteArray.u64(at: Int): Long {
            var value = 0L
            for (i in 0 until LONG) value = value or ((this[at + i].toLong() and BYTE_LONG) shl (8 * i))
            return value
        }

        private const val HEADER_LENGTH = 127
        private const val VERSION = 3
        private const val LONG = 8
        private const val ROOT_OFFSET = 8
        private const val LEAF_OFFSET = 40
        private const val DATA_OFFSET = 56
        private const val MIN_ZOOM = 100
        private const val MAX_ZOOM = 101
        private const val BYTE_MASK = 0xFF
        private const val BYTE_LONG = 0xFFL
        private const val VARINT_PAYLOAD = 0x7F
        private const val VARINT_MORE = 0x80
        private const val VARINT_BITS = 7

        private val MAGIC = "PMTiles".encodeToByteArray()
    }
}
