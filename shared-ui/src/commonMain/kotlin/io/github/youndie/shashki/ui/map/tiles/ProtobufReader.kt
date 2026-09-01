package io.github.youndie.shashki.ui.map.tiles

/**
 * As much of protobuf as a vector tile needs, and no more.
 *
 * **Hand-written rather than generated, and that is a measurement in B-01's favour rather than a
 * preference.** `kotlinx-serialization-protobuf` publishes a Wasm target (research §1.8) and would
 * do this — but it wants a schema, and the whole of MVT's wire format is four wire types and one
 * packed repeated field. WorldWind's MVT package reached the same conclusion and ships its own
 * `ProtobufReader`; two independent implementations choosing the same thing is worth a sentence.
 *
 * Not a general reader: it knows nothing of groups, of 32-bit fixed fields, or of maps. What it
 * cannot read it skips by wire type, which is exactly what protobuf's forward-compatibility rule
 * asks of a reader — a tile from a newer encoder loses the fields this does not know and keeps the
 * rest.
 */
internal class ProtobufReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val end: Int = bytes.size,
) {
    val hasMore: Boolean get() = position < end

    /** The tag of the next field: `(number shl 3) or wireType`. */
    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(position < end) { "varint runs past the end of the message at $position" }
            val b = bytes[position++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift < 64) { "varint longer than 64 bits at $position" }
        }
    }

    /** Signed varints are zigzag-encoded, which is how a geometry's deltas fit in one byte. */
    fun readZigZag(): Int {
        val raw = readVarint()
        return ((raw shr 1) xor -(raw and 1)).toInt()
    }

    fun readString(): String {
        val length = readVarint().toInt()
        val s = bytes.decodeToString(position, position + length)
        position += length
        return s
    }

    /** A nested message or a packed repeated field: a reader bounded to its own bytes. */
    fun readMessage(): ProtobufReader {
        val length = readVarint().toInt()
        val nested = ProtobufReader(bytes, position, position + length)
        position += length
        return nested
    }

    fun skip(tag: Int) {
        when (tag and 0x7) {
            WIRE_VARINT -> readVarint()
            WIRE_64BIT -> position += 8
            WIRE_LENGTH -> position += readVarint().toInt()
            WIRE_32BIT -> position += 4
            else -> error("unsupported wire type ${tag and 0x7}")
        }
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_64BIT = 1
        const val WIRE_LENGTH = 2
        const val WIRE_32BIT = 5

        fun fieldOf(tag: Int): Int = tag ushr 3
    }
}
