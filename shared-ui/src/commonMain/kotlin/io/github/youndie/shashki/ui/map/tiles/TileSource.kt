package io.github.youndie.shashki.ui.map.tiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.youndie.shashki.ui.map.TileCoordinate
import kotlinx.coroutines.CancellationException

/**
 * Where a surface gets its tiles, and — just as importantly — what it already has.
 *
 * **Two methods, because drawing cannot wait.** Composition draws with what is in memory *now*;
 * fetching happens beside it and puts the result somewhere composition is watching. A single
 * `suspend fun tile()` would make every frame depend on a coroutine having finished, which in a
 * screenshot test means a picture of an empty map and in an application means a flash of one.
 *
 * The absence of a tile is not an error at either end: a viewport at the edge of a city extract
 * straddles tiles the archive never held.
 */
public interface TileSource {
    /** What is in memory. Called from composition; never blocks, never fetches. */
    public fun loaded(coordinate: TileCoordinate): MvtTile?

    /** Fetch [coordinate] and remember it, so a later [loaded] answers. Idempotent. */
    public suspend fun load(coordinate: TileCoordinate)

    /**
     * Why there will be no basemap, or `null` while there is one coming (B-56).
     *
     * **The absence of a tile is not an error and the absence of the archive is not fatal.** This
     * type's own note says the map is the one part of the product that can be missing without the
     * screen being wrong — and until this existed, an archive that answered 404 threw out of the
     * `LaunchedEffect` that was fetching, took the composition with it, and left a black page with
     * no words on it. A basemap that cannot be reached is a fact the surface can draw.
     */
    public val unavailable: String? get() = null
}

/**
 * Tiles that are already here — a fixture's, or a test's.
 *
 * **It is not a stub.** The single-tile goldens and the seam golden both need a map drawn from real
 * city bytes with no archive, no transport and no coroutine, and this is what "the tiles are already
 * in memory" looks like as a type.
 */
public class MemoryTileSource(
    private val tiles: Map<TileCoordinate, MvtTile>,
) : TileSource {
    override fun loaded(coordinate: TileCoordinate): MvtTile? = tiles[coordinate]

    override suspend fun load(coordinate: TileCoordinate): Unit = Unit
}

/** No basemap at all. What a screen has before an archive is bound — see `CanvasMapSurface`. */
public object NoTiles : TileSource {
    override fun loaded(coordinate: TileCoordinate): MvtTile? = null

    override suspend fun load(coordinate: TileCoordinate): Unit = Unit
}

/**
 * The archive, decoded once per tile and kept.
 *
 * **The cache is the whole point and its absence is the failure mode B-30 names.** A surface that
 * fetched per frame would draw the same city and would be indefensible — sixty ranged reads a second
 * for a map that has not moved. So a coordinate is fetched at most once: `misses` records the
 * decisions to go to the archive, and a test holds the number to what a pan should cost.
 *
 * **Nothing is evicted.** A city extract is 810 tiles and a viewport holds four; a demo that panned
 * across the whole of it would hold about 17 MB of decoded geometry, which is a number worth knowing
 * rather than a leak worth guarding. An eviction policy on a bounded archive would be code with no
 * behaviour to test.
 */
public class PmtilesTileSource(
    /**
     * Opened on the first tile, not at construction.
     *
     * **Because opening is two network reads and a graph is built synchronously.** A DI module that
     * had to await the header and the root directory would make the application's start depend on
     * an object store being up — and the map is the one part of this product that can be missing
     * without the screen being wrong.
     */
    private val open: suspend () -> PmtilesArchive,
) : TileSource {
    // Compose state, because the point of loading is that the canvas redraws when it lands.
    private val tiles = mutableStateMapOf<TileCoordinate, MvtTile?>()

    /** How many times this went to the archive. The read count B-30 asks to be held to. */
    public var misses: Int = 0
        private set

    override fun loaded(coordinate: TileCoordinate): MvtTile? = tiles[coordinate]

    private var archive: PmtilesArchive? = null

    /**
     * Set once, and it stops the source trying (B-56).
     *
     * **A failure to reach the archive is remembered rather than repeated**: the viewport asks for
     * four tiles a frame, so an archive that is not there would otherwise be a failed range request
     * per tile per pan, for ever. One attempt, one answer, and the surface says the basemap is
     * unavailable.
     */
    override var unavailable: String? by mutableStateOf(null)
        private set

    override suspend fun load(coordinate: TileCoordinate) {
        if (unavailable != null || tiles.containsKey(coordinate)) return
        misses++
        try {
            val opened = archive ?: open().also { archive = it }
            // Recorded even when the archive has nothing there, so a hole is asked about once rather
            // than on every recomposition for as long as the map is pointed at the edge of the
            // extract.
            tiles[coordinate] = opened.tile(coordinate)?.let(::decodeMvt)
        } catch (failure: CancellationException) {
            // Going away is not the archive's fault: a cancelled load must stay cancelled, and
            // recording it as unavailable would turn a navigation into a permanent missing map.
            throw failure
        } catch (failure: Throwable) {
            unavailable = failure.message ?: "the map archive could not be read"
        }
    }
}
