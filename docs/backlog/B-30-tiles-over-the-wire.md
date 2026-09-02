---
id: B-30
title: "The map fetches its tiles, so the streets are there"
status: open
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
