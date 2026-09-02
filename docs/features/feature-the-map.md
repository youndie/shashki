---
id: feature-the-map
title: The map
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries:
  - screen-rider-class-picker
  - screen-rider-trip
api: []
tags: [client]
---

# The map

## 1. Overview

The basemap is drawn by this product rather than by a map library: a pmtiles archive read over ranged
HTTP, vector tiles decoded and painted on a Compose canvas, with the route, the car and the pins on
the same canvas as everything above them.

**It is drawn by us because of what a screenshot has to contain.** Every other route to a browser map
puts the map on a surface Compose does not own — a DOM canvas behind a transparent one — so no golden
of such a screen can exist, and the map is a hole in the picture. This one is an ordinary composable:
`modifier` means what it means everywhere else, and the screen appears in the golden suite like any
other. [D1](../research/research-architecture.md) is the argument and the three rejected routes.

## 1a. What happens when the archive is not there (B-56)

**The basemap can be missing without the screen being wrong**, and until B-56 it could not: a range
read that came back 404 threw out of the `LaunchedEffect` that was fetching, took the composition
with it, and left a black rectangle with no words on it. Following this repository's own quickstart
produced exactly that, because the archive it told a reader to upload had a name nothing here
produces.

`PmtilesTileSource` now records the failure instead of raising it, stops going back to an archive it
knows is not there — a viewport asks for four tiles a frame — and `CanvasMapSurface` draws
`no map: <what failed>` in the subtle brush. Everything that is not the basemap still draws: the
route, the pins and the car come from the server, not from the archive.

Beside it, and independent of it, both bundles install a **fatal band** before the application
starts: plain DOM markup that names an uncaught failure across the top of the page. It is in the DOM
rather than in Compose because by the time it is needed Compose is what died, and it is beside the
crash reporter rather than inside it — katcher hears about the failure either way.

## 2. Business rules

* The map has no server of its own. The archive is a static object and the browser reads bytes of it
  by `Range`; shashki links nothing from the object store.
* No archive configured is a running configuration: the map paints the style's own background and
  everything the server said — the road, the car, the pins — in the right place, on top of it.
* A tile the archive does not hold is a miss, not an error. A city extract has edges.
* Reads are counted: opening costs two requests, each tile costs one, and a miss costs none. A
  surface that fetched per frame would draw the same city and would be indefensible.

## 3. Flow

1. The camera decides which tiles the pane touches — the plane is drawn at `floor(zoom)` and scaled
   by the remainder, so a half-zoom does not fetch a different set.
2. Missing tiles are fetched beside the drawing, not inside it: the canvas paints what is in memory
   and the source's own state brings the frame back when a tile lands.
3. Each tile is read from the archive by its Hilbert id, decompressed and decoded.
4. Every tile's areas are drawn, then every tile's roads, then the labels — **across** tiles rather
   than within them.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/CanvasMapSurface.kt` |
| shashki-server | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/tiles/` — pmtiles, inflate, MVT, the renderer |
| shashki-server | `map/tile_serving.py`, `map/pmtiles_subset.py` — the deployment recipe and the fixture cutter |

## 5. Scenarios

### Scenario: the reader agrees with a tool chain that is not ours

* **Given:** a tile extracted from the city archive by `pmtiles`
* **When:** this reader walks the archive and hands back the same tile
* **Then:** the bytes are identical — one assertion covering the header parse, the directory walk, the
  Hilbert id and a hand-written inflater
* **Automated:** `shashki PmtilesArchiveTest`

### Scenario: a viewport costs one read per tile

* **Given:** the city's archive on a running object store
* **When:** a pane on the corner where four tiles meet is drawn, twice
* **Then:** two requests to open, four to draw, and none for the second look
* **Automated:** `shashki TilesOverHttpTest`

### Scenario: the seam

* **Given:** a camera on the corner of four tiles
* **When:** the map is drawn
* **Then:** roads cross the boundary without stopping, and a street name that spans two tiles is
  written once
* **Automated:** `shashki CanvasTilesAtASeam` — the fixture behind `map_canvas_tiles_at_a_seam.png`,
  compared by `viddikVerify` on every `check`

## 6. Out of scope

* **Reading the style documents.** The colours, the filters and the road bands are transcribed as
  Kotlin; a style interpreter is sized in research §1.8 and not built.
* **Pan and pinch.** The camera comes from the scene; gestures are the easy end and nothing needs them.
* Repeating a label down a long street, and pushing one along its road to find a free spot.

## 7. Quirks

* **Clipping at seams is the opposite of what a seam needs.** MVT geometry runs past the tile's own
  edge so neighbours meet; what the seam needs is *ordering across tiles*, or one tile's water covers
  its neighbour's streets in the overlap.
* **The inflater is written here.** okio publishes none for wasmJs — checked inside the artefact — and
  the browser's `DecompressionStream` would make every read asynchronous and split the shipping target
  from the photographable one. It checks the gzip trailer's CRC, because a wrong inflater does not
  throw: it produces plausible bytes, and a tile of plausible bytes draws a city that is not there.
* **Street names are wound to read left to right.** A road digitised east-to-west came out mirrored,
  and the earlier goldens had recorded that as correct.
