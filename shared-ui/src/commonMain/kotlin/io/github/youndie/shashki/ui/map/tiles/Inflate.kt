package io.github.youndie.shashki.ui.map.tiles

/**
 * DEFLATE and the gzip wrapper around it, in common code.
 *
 * **Written by hand because there is nowhere to borrow it from on the target that matters.** The
 * archive is gzipped inside and out — the directory and every tile — and the product runs in a
 * browser on Kotlin/Wasm. `java.util.zip` is the JVM's. okio has `GzipSource`, and its wasmJs
 * artifact does not carry it: the klib for 3.18.1 has no inflate at all, checked rather than
 * assumed. The browser has `DecompressionStream`, which would work and would make every read
 * asynchronous, drag in the DOM stream types, and leave the desktop target — the one that can be
 * photographed — on a different implementation from the one that ships.
 *
 * So it is the same decision `ProtobufReader` records: this project already writes its own reader
 * for the tile's own format, and route 4 exists because the map is ours.
 *
 * **It checks its own CRC**, which is the part that is not optional. A hand-written inflater that is
 * subtly wrong does not throw — it produces plausible bytes, and a tile made of plausible bytes
 * draws a city that is not there. The gzip trailer carries CRC-32 and the uncompressed length, so
 * the format itself says whether the output is right, and this refuses to hand back output that
 * fails either.
 *
 * The decoder is the canonical-code walk from zlib's own `puff`: counts per length, symbols in
 * order, and a code accumulated one bit at a time until it falls inside the current length's range.
 */
internal fun gunzip(bytes: ByteArray): ByteArray {
    require(bytes.size > GZIP_MINIMUM) { "not gzip: ${bytes.size} bytes is too short to be a member" }
    require(bytes[0] == MAGIC_0 && bytes[1] == MAGIC_1) { "not gzip: magic is not 1f 8b" }
    require(bytes[2].toInt() == DEFLATE) { "gzip member is not deflate (method ${bytes[2].toInt()})" }

    val flags = bytes[3].toInt()
    var at = GZIP_HEADER
    if (flags and F_EXTRA != 0) at += EXTRA_LENGTH + (bytes.u8(at) or (bytes.u8(at + 1) shl 8))
    if (flags and F_NAME != 0) at = bytes.afterZero(at)
    if (flags and F_COMMENT != 0) at = bytes.afterZero(at)
    if (flags and F_HCRC != 0) at += HCRC_LENGTH

    val out = inflate(bytes, at)

    val trailer = bytes.size - GZIP_TRAILER
    val declaredCrc = bytes.u32(trailer)
    val declaredSize = bytes.u32(trailer + CRC_LENGTH)
    check(out.size.toLong() and MASK_32 == declaredSize) {
        "gzip says $declaredSize bytes and inflating produced ${out.size}"
    }
    check(crc32(out) == declaredCrc) { "gzip CRC-32 does not match: the inflated bytes are not the original" }
    return out
}

/** Raw DEFLATE from [from]. Public only to this module; the wrapper above is the entry point. */
private fun inflate(
    source: ByteArray,
    from: Int,
): ByteArray {
    val bits = BitReader(source, from)
    var out = ByteArray(INITIAL_OUTPUT)
    var size = 0

    fun put(byte: Byte) {
        if (size == out.size) out = out.copyOf(out.size * 2)
        out[size++] = byte
    }

    while (true) {
        val last = bits.bits(1) == 1
        when (val type = bits.bits(2)) {
            STORED -> {
                bits.align()
                val length = bits.bits(BYTE_BITS) or (bits.bits(BYTE_BITS) shl BYTE_BITS)
                // The complement is in the stream and is not checked: it repeats the length, and a
                // length that is wrong makes the next block unreadable a few bytes later anyway.
                bits.bits(BYTE_BITS)
                bits.bits(BYTE_BITS)
                repeat(length) { put(bits.byte()) }
            }

            FIXED, DYNAMIC -> {
                val (literals, distances) =
                    if (type == FIXED) fixedTables() else bits.dynamicTables()
                while (true) {
                    val symbol = literals.decode(bits)
                    if (symbol < END_OF_BLOCK) {
                        put(symbol.toByte())
                    } else if (symbol == END_OF_BLOCK) {
                        break
                    } else {
                        val index = symbol - END_OF_BLOCK - 1
                        val length = LENGTH_BASE[index] + bits.bits(LENGTH_EXTRA[index])
                        val distanceSymbol = distances.decode(bits)
                        val distance = DISTANCE_BASE[distanceSymbol] + bits.bits(DISTANCE_EXTRA[distanceSymbol])
                        check(distance <= size) { "back-reference $distance beyond $size bytes of output" }
                        // Byte at a time on purpose: an overlapping copy is how a run is encoded,
                        // and a block move would read bytes this loop is still writing.
                        var back = size - distance
                        repeat(length) { put(out[back++]) }
                    }
                }
            }

            else -> {
                error("reserved deflate block type $type")
            }
        }
        if (last) break
    }
    return out.copyOf(size)
}

/** LSB-first, which is what deflate is and what makes every off-by-one here silent. */
private class BitReader(
    private val source: ByteArray,
    private var at: Int,
) {
    private var hold = 0
    private var count = 0

    fun bits(want: Int): Int {
        while (count < want) {
            hold = hold or (source.u8(at++) shl count)
            count += BYTE_BITS
        }
        val value = hold and ((1 shl want) - 1)
        hold = hold ushr want
        count -= want
        return value
    }

    /** Drop to the next byte boundary — what a stored block starts on. */
    fun align() {
        hold = 0
        count = 0
    }

    fun byte(): Byte = source[at++]

    /**
     * The two tables a dynamic block carries in front of itself, which are themselves Huffman coded.
     */
    fun dynamicTables(): Pair<Huffman, Huffman> {
        val literals = bits(HLIT_BITS) + MIN_LITERALS
        val distances = bits(HDIST_BITS) + 1
        val codes = bits(HCLEN_BITS) + MIN_CODE_LENGTHS

        val codeLengths = IntArray(CODE_LENGTH_ORDER.size)
        for (i in 0 until codes) codeLengths[CODE_LENGTH_ORDER[i]] = bits(CODE_LENGTH_BITS)
        val lengthTable = Huffman(codeLengths)

        val all = IntArray(literals + distances)
        var i = 0
        while (i < all.size) {
            when (val symbol = lengthTable.decode(this)) {
                REPEAT_PREVIOUS -> {
                    check(i > 0) { "a run of the previous code length with no previous code length" }
                    val previous = all[i - 1]
                    repeat(bits(REPEAT_PREVIOUS_BITS) + REPEAT_PREVIOUS_MIN) { all[i++] = previous }
                }

                REPEAT_ZERO_SHORT -> {
                    repeat(bits(REPEAT_ZERO_SHORT_BITS) + REPEAT_ZERO_SHORT_MIN) { all[i++] = 0 }
                }

                REPEAT_ZERO_LONG -> {
                    repeat(bits(REPEAT_ZERO_LONG_BITS) + REPEAT_ZERO_LONG_MIN) { all[i++] = 0 }
                }

                else -> {
                    all[i++] = symbol
                }
            }
        }
        return Huffman(all.copyOfRange(0, literals)) to Huffman(all.copyOfRange(literals, all.size))
    }
}

/** A canonical Huffman table: how many codes of each length, and the symbols in code order. */
private class Huffman(
    lengths: IntArray,
) {
    private val counts = IntArray(MAX_CODE_BITS + 1)
    private val symbols = IntArray(lengths.size)

    init {
        for (length in lengths) counts[length]++
        counts[0] = 0
        val offsets = IntArray(MAX_CODE_BITS + 1)
        for (length in 1..MAX_CODE_BITS) offsets[length] = offsets[length - 1] + counts[length - 1]
        for (symbol in lengths.indices) {
            if (lengths[symbol] != 0) symbols[offsets[lengths[symbol]]++] = symbol
        }
    }

    fun decode(bits: BitReader): Int {
        var code = 0
        var first = 0
        var index = 0
        for (length in 1..MAX_CODE_BITS) {
            code = code or bits.bits(1)
            val count = counts[length]
            if (code - first < count) return symbols[index + (code - first)]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        error("incomplete huffman code: no symbol in $MAX_CODE_BITS bits")
    }
}

/** The table every fixed block uses, built once per call rather than kept — blocks are rare. */
private fun fixedTables(): Pair<Huffman, Huffman> {
    val literals = IntArray(FIXED_LITERALS)
    for (i in literals.indices) {
        literals[i] =
            when {
                i < FIXED_8_BIT_END -> 8
                i < FIXED_9_BIT_END -> 9
                i < FIXED_7_BIT_END -> 7
                else -> 8
            }
    }
    return Huffman(literals) to Huffman(IntArray(FIXED_DISTANCES) { FIXED_DISTANCE_BITS })
}

/** The gzip trailer's checksum, table-free: the format asserts it, so it is worth computing. */
private fun crc32(bytes: ByteArray): Long {
    var crc = MASK_32
    for (byte in bytes) {
        crc = crc xor (byte.toLong() and BYTE_MASK)
        repeat(BYTE_BITS) {
            crc = if (crc and 1L != 0L) (crc ushr 1) xor CRC_POLYNOMIAL else crc ushr 1
        }
    }
    return crc xor MASK_32
}

private fun ByteArray.u8(at: Int): Int = this[at].toInt() and BYTE_MASK.toInt()

private fun ByteArray.u32(at: Int): Long =
    (u8(at).toLong()) or
        (u8(at + 1).toLong() shl 8) or
        (u8(at + 2).toLong() shl 16) or
        (u8(at + 3).toLong() shl 24)

private fun ByteArray.afterZero(from: Int): Int {
    var at = from
    while (this[at].toInt() != 0) at++
    return at + 1
}

private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFFL
private const val MASK_32 = 0xFFFFFFFFL
private const val CRC_POLYNOMIAL = 0xEDB88320L

private const val MAGIC_0 = 0x1F.toByte()
private const val MAGIC_1 = 0x8B.toByte()
private const val DEFLATE = 8
private const val GZIP_HEADER = 10
private const val GZIP_TRAILER = 8
private const val GZIP_MINIMUM = GZIP_HEADER + GZIP_TRAILER
private const val CRC_LENGTH = 4
private const val EXTRA_LENGTH = 2
private const val HCRC_LENGTH = 2
private const val F_HCRC = 0x02
private const val F_EXTRA = 0x04
private const val F_NAME = 0x08
private const val F_COMMENT = 0x10

private const val STORED = 0
private const val FIXED = 1
private const val DYNAMIC = 2
private const val END_OF_BLOCK = 256
private const val MAX_CODE_BITS = 15
private const val INITIAL_OUTPUT = 1 shl 16

private const val HLIT_BITS = 5
private const val HDIST_BITS = 5
private const val HCLEN_BITS = 4
private const val CODE_LENGTH_BITS = 3
private const val MIN_LITERALS = 257
private const val MIN_CODE_LENGTHS = 4
private const val REPEAT_PREVIOUS = 16
private const val REPEAT_PREVIOUS_BITS = 2
private const val REPEAT_PREVIOUS_MIN = 3
private const val REPEAT_ZERO_SHORT = 17
private const val REPEAT_ZERO_SHORT_BITS = 3
private const val REPEAT_ZERO_SHORT_MIN = 3
private const val REPEAT_ZERO_LONG = 18
private const val REPEAT_ZERO_LONG_BITS = 7
private const val REPEAT_ZERO_LONG_MIN = 11

private const val FIXED_LITERALS = 288
private const val FIXED_DISTANCES = 30
private const val FIXED_DISTANCE_BITS = 5
private const val FIXED_8_BIT_END = 144
private const val FIXED_9_BIT_END = 256
private const val FIXED_7_BIT_END = 280

private val CODE_LENGTH_ORDER =
    intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

private val LENGTH_BASE =
    intArrayOf(
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        13,
        15,
        17,
        19,
        23,
        27,
        31,
        35,
        43,
        51,
        59,
        67,
        83,
        99,
        115,
        131,
        163,
        195,
        227,
        258,
    )

private val LENGTH_EXTRA =
    intArrayOf(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        1,
        2,
        2,
        2,
        2,
        3,
        3,
        3,
        3,
        4,
        4,
        4,
        4,
        5,
        5,
        5,
        5,
        0,
    )

private val DISTANCE_BASE =
    intArrayOf(
        1,
        2,
        3,
        4,
        5,
        7,
        9,
        13,
        17,
        25,
        33,
        49,
        65,
        97,
        129,
        193,
        257,
        385,
        513,
        769,
        1025,
        1537,
        2049,
        3073,
        4097,
        6145,
        8193,
        12289,
        16385,
        24577,
    )

private val DISTANCE_EXTRA =
    intArrayOf(
        0,
        0,
        0,
        0,
        1,
        1,
        2,
        2,
        3,
        3,
        4,
        4,
        5,
        5,
        6,
        6,
        7,
        7,
        8,
        8,
        9,
        9,
        10,
        10,
        11,
        11,
        12,
        12,
        13,
        13,
    )
