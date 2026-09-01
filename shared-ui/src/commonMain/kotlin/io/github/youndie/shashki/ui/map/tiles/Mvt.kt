package io.github.youndie.shashki.ui.map.tiles

import androidx.compose.runtime.Immutable
import io.github.youndie.shashki.ui.map.tiles.ProtobufReader.Companion.fieldOf

/**
 * A decoded vector tile: the layers a style names, with their features in **tile coordinates**.
 *
 * Coordinates are integers from 0 to [MvtLayer.extent] — the tile's own grid, not the world's.
 * Turning them into screen pixels is the renderer's job and depends on the camera, which is why
 * nothing here mentions a `GeoPoint`: a decoder that projected would have to know where the map is
 * looking, and then a tile could not be decoded once and drawn twice.
 */
@Immutable
public data class MvtTile(
    val layers: List<MvtLayer>,
) {
    public fun layer(name: String): MvtLayer? = layers.firstOrNull { it.name == name }
}

@Immutable
public data class MvtLayer(
    val name: String,
    val extent: Int,
    val features: List<MvtFeature>,
)

@Immutable
public data class MvtFeature(
    val id: Long,
    val type: MvtGeometryType,
    /** The style's `filter` reads these — `class`, `name:latin`, `name`. */
    val tags: Map<String, String>,
    /**
     * One list per ring or per line; a point feature has one list of one. Kept as flat parallel
     * arrays of x and y rather than a list of points, because a tile holds tens of thousands of
     * them and every one would otherwise be an object.
     */
    val paths: List<IntArray>,
)

public enum class MvtGeometryType { UNKNOWN, POINT, LINESTRING, POLYGON }

/**
 * Decodes one tile. The bytes are the tile as the archive stores it, already decompressed.
 *
 * The geometry encoding is the part worth knowing: a command integer packs an id in its low three
 * bits and a repeat count in the rest — `MoveTo` 1, `LineTo` 2, `ClosePath` 7 — and the parameters
 * that follow are zigzag deltas from the previous point. So a road is a `MoveTo` of one followed by
 * a `LineTo` of many, and a multi-part road is that twice.
 */
public fun decodeMvt(bytes: ByteArray): MvtTile {
    val reader = ProtobufReader(bytes)
    val layers = mutableListOf<MvtLayer>()
    while (reader.hasMore) {
        val tag = reader.readTag()
        if (fieldOf(tag) == TILE_LAYERS) layers += decodeLayer(reader.readMessage()) else reader.skip(tag)
    }
    return MvtTile(layers)
}

private fun decodeLayer(reader: ProtobufReader): MvtLayer {
    var name = ""
    var extent = DEFAULT_EXTENT
    val keys = mutableListOf<String>()
    val values = mutableListOf<String>()
    val featureBodies = mutableListOf<ProtobufReader>()

    while (reader.hasMore) {
        val tag = reader.readTag()
        when (fieldOf(tag)) {
            LAYER_NAME -> name = reader.readString()
            LAYER_FEATURES -> featureBodies += reader.readMessage()
            LAYER_KEYS -> keys += reader.readString()
            LAYER_VALUES -> values += decodeValue(reader.readMessage())
            LAYER_EXTENT -> extent = reader.readVarint().toInt()
            else -> reader.skip(tag)
        }
    }
    // Features are decoded after the whole layer is read: a feature's tags index into the key and
    // value tables, and protobuf does not promise those come first.
    return MvtLayer(name, extent, featureBodies.map { decodeFeature(it, keys, values) })
}

/**
 * A tag value is one of seven types and this returns a string for every one.
 *
 * The styles compare `class` to a string and put `name` on a label; none of them does arithmetic on
 * a tag. Keeping a sealed value type would be honest about the wire and useless at every call site,
 * so the numbers are formatted here and the reason is written down rather than discovered.
 */
private fun decodeValue(reader: ProtobufReader): String {
    var out = ""
    while (reader.hasMore) {
        val tag = reader.readTag()
        when (fieldOf(tag)) {
            VALUE_STRING -> out = reader.readString()
            VALUE_INT, VALUE_UINT -> out = reader.readVarint().toString()
            VALUE_SINT -> out = reader.readZigZag().toString()
            VALUE_BOOL -> out = (reader.readVarint() != 0L).toString()
            else -> reader.skip(tag)
        }
    }
    return out
}

private fun decodeFeature(
    reader: ProtobufReader,
    keys: List<String>,
    values: List<String>,
): MvtFeature {
    var id = 0L
    var type = MvtGeometryType.UNKNOWN
    var tagIndices: IntArray = IntArray(0)
    var geometry: IntArray = IntArray(0)

    while (reader.hasMore) {
        val tag = reader.readTag()
        when (fieldOf(tag)) {
            FEATURE_ID -> {
                id = reader.readVarint()
            }

            FEATURE_TAGS -> {
                tagIndices = readPackedVarints(reader.readMessage())
            }

            FEATURE_TYPE -> {
                type =
                    MvtGeometryType.entries.getOrElse(reader.readVarint().toInt()) { MvtGeometryType.UNKNOWN }
            }

            FEATURE_GEOMETRY -> {
                geometry = readPackedVarints(reader.readMessage())
            }

            else -> {
                reader.skip(tag)
            }
        }
    }

    val tags =
        buildMap {
            var i = 0
            while (i + 1 < tagIndices.size) {
                val key = keys.getOrNull(tagIndices[i])
                val value = values.getOrNull(tagIndices[i + 1])
                if (key != null && value != null) put(key, value)
                i += 2
            }
        }
    return MvtFeature(id, type, tags, decodeGeometry(geometry))
}

private fun readPackedVarints(reader: ProtobufReader): IntArray {
    val out = ArrayList<Int>()
    while (reader.hasMore) out += reader.readVarint().toInt()
    return out.toIntArray()
}

/** Command integers to paths, in tile coordinates. See [decodeMvt]'s note on the encoding. */
private fun decodeGeometry(commands: IntArray): List<IntArray> {
    val paths = mutableListOf<IntArray>()
    var current = ArrayList<Int>()
    var x = 0
    var y = 0
    var i = 0
    while (i < commands.size) {
        val command = commands[i] and 0x7
        val count = commands[i] ushr 3
        i++
        when (command) {
            COMMAND_MOVE_TO -> {
                repeat(count) {
                    if (current.size >= 2) {
                        paths += current.toIntArray()
                        current = ArrayList()
                    }
                    x += zigzag(commands[i++])
                    y += zigzag(commands[i++])
                    current += x
                    current += y
                }
            }

            COMMAND_LINE_TO -> {
                repeat(count) {
                    x += zigzag(commands[i++])
                    y += zigzag(commands[i++])
                    current += x
                    current += y
                }
            }

            // A closed ring repeats its first point rather than carrying a flag, so a renderer that
            // knows nothing about polygons still draws the outline correctly.
            COMMAND_CLOSE_PATH -> {
                if (current.size >= 2) {
                    current += current[0]
                    current += current[1]
                }
            }

            else -> {
                error("unknown geometry command $command")
            }
        }
    }
    if (current.size >= 2) paths += current.toIntArray()
    return paths
}

private fun zigzag(value: Int): Int = (value shr 1) xor -(value and 1)

private const val DEFAULT_EXTENT = 4096

private const val TILE_LAYERS = 3
private const val LAYER_NAME = 1
private const val LAYER_FEATURES = 2
private const val LAYER_KEYS = 3
private const val LAYER_VALUES = 4
private const val LAYER_EXTENT = 5
private const val VALUE_STRING = 1
private const val VALUE_INT = 4
private const val VALUE_UINT = 5
private const val VALUE_SINT = 6
private const val VALUE_BOOL = 7
private const val FEATURE_ID = 1
private const val FEATURE_TAGS = 2
private const val FEATURE_TYPE = 3
private const val FEATURE_GEOMETRY = 4
private const val COMMAND_MOVE_TO = 1
private const val COMMAND_LINE_TO = 2
private const val COMMAND_CLOSE_PATH = 7
