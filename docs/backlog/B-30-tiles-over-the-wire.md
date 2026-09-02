---
id: B-30
title: "The map fetches its tiles, so the streets are there"
status: done
priority: P1
size: L
stage: stage-3-surface
---

# B-30 — The map fetches its tiles, so the streets are there

Route 4 draws a tile beautifully and has no way to get one. `CanvasMapSurface` takes an `MvtTile` and
the application passes none, so the map is the style document's own background with the route, the
car and the pins on it — in the right place, and with no streets. This is the half of §1.8b that was
always named as the cost and never built.

- **The reading half is done and measured.** The pmtiles container is understood rather than assumed
  (B-01: v3, one root directory and no leaves, gzip inside and out, Hilbert tile ids), the MVT
  decoder is written and tested against a second reader, and [B-07](B-07-serve-pmtiles-from-bochka.md)
  measured the transport: 812 ranged reads at p99 under 3.2 ms, with the CORS and bucket-policy
  switches a browser needs.
- **What is missing is Kotlin.** The pmtiles directory reader exists in Python
  (`map/tile_serving.py`) and nowhere else; a browser needs it in the bundle.
- **And a camera.** One tile is a viewport of one tile: the projection already places anything at a
  given tile, but panning, zooming and drawing the four tiles a screen straddles are not there.
  §1.8b lists this with clipping at seams and label collision.
- The rejected alternative is a raster basemap from somebody's server. It would work tomorrow and
  would delete D1's whole argument — the reason the map is ours is that a Compose canvas is the only
  surface that honours a modifier and appears in a golden.

- AC: the rider's map draws the city's streets, fetched over ranged HTTP from where
  [B-07](B-07-serve-pmtiles-from-bochka.md) put the archive.
- AC: a golden of a screen whose map straddles a tile seam, because the seam is where a tiling
  renderer looks wrong first.
- AC: the reads are counted in a test — a screen that fetched a tile per frame would work and would
  be indefensible.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/CanvasMapSurface.kt`,
  `map/tile_serving.py`

## What it turned out to be

**The list §1.8b wrote three months of caution into was mostly right and wrong about two things,
one of which was not on it at all.**

Right: the pmtiles directory, the ranged reads, the Web Mercator transform, the tile selection, the
cache and the label collision are all built, and none of them was large. The directory is four runs
of varints; the header is five offset pairs; the camera is sixty lines, most of which already existed
as `TileProjection`.

**Wrong, first: clipping at seams is the opposite of what a seam needs.** MVT geometry runs past the
tile's own edge by a buffer so that a neighbour draws the same road and the two meet — clipping at
the boundary is what makes a road stop dead, not what prevents it. What the seam actually needs is
**ordering across tiles rather than within them**: every tile's areas, then every tile's roads, or
one tile's water covers its neighbour's street network in the overlap. That is why `drawTile` became
`drawTileAreas` and `drawTileRoads`, and it is a two-line change nobody would guess from the
instruction that was written down.

**Wrong, second: gzip was not on the list.** The archive is gzipped inside and out and there is
nowhere to borrow an inflater from on the target the product ships on — okio's wasmJs klib carries no
inflate at all, which was checked inside the artefact rather than assumed, and the browser's
`DecompressionStream` would make every read asynchronous and put the photographable target on a
different implementation from the shipping one. So DEFLATE and the gzip wrapper are written here, in
common code, checking the trailer's CRC-32. That check is not decoration: a hand-written inflater
that is subtly wrong does not throw, it produces plausible bytes, and a tile of plausible bytes draws
a city that is not there.

**The oracle is a file that was already in the repository.** `ljubljana-14-8850-5815.mvt` was
extracted for B-01 by `pmtiles`; `map/pmtiles_subset.py` cuts five of the city archive's tiles into a
300 kB fixture without re-encoding any of them. If the header parse, the directory walk, the Hilbert
id and the inflater are all right, the bytes that come back for that tile are that file — 4 068 of
them, identical. One assertion over four things that are each easy to get subtly wrong, and it passed
first try.

**The counting criterion is met twice.** Against the fixture: opening is two reads, each tile is one,
a miss is none. Against the real 17 MB archive on a running bochka: 810 tiles known from two requests,
four reads to draw a corner, and a second look at the same viewport costs nothing. `docker/compose.yaml`
now stands the store up and `docker/upload-tiles.sh` sets the two switches B-07 found — the bucket
policy, because a browser cannot sign a request, and the CORS rule, because `Range` is not on the
safelist and every tile read is preflighted.

**Two defects the goldens found once the map had streets on it.** Street names were drawn along the
road's own digitised direction, so half of them were upside down — and the earlier goldens had duly
recorded that as correct, because a screenshot test certifies whatever it is shown. And every label
was drawn per tile, so a street crossing a seam had its name written twice and a street split by
junctions had it written five times; the fix is one greedy pass over the whole viewport, longest road
first, one placement per name.

**A fixture that pointed at the wrong ground.** The prototype took its frame from a fixed
`TileCoordinate` and ignored the camera, so the fixtures' camera — Ljubljana's own centre — had no
effect on the picture and nobody noticed it was three tiles away from the tile being drawn. With a
real viewport the camera picks the tiles, and a fixture pointing at ground the archive does not hold
draws an empty map.

**Still not built, and named:** the style documents are still transcribed as Kotlin rather than
interpreted, labels are placed once rather than repeated along a long street, and there is no pan or
pinch — the camera comes from the scene. None of those is what B-30 asked for.

Goldens: `map_canvas_tiles_at_a_seam` is new and the six that draw a map were re-recorded, all on the
mac and verified unchanged on Linux by the same `check` — so B-02's portability claim now covers a
hand-written inflater and a collision pass, not only text.
