---
id: B-06
title: "Produce the OSM extract and the pmtiles archive for Ljubljana"
status: done
priority: P1
size: M
stage: stage-0-unknowns
---

# B-06 — Produce the OSM extract and the pmtiles archive for Ljubljana

Open question 3 in the research. The city sits upstream of more than it looks: it fixes the size of
the pmtiles archive the browser has to range-read, the GraphHopper import time, and the street names
in every fixture — which means it is upstream of the goldens too.

**The city is chosen: Ljubljana** (research §3, open question 3), on a compact graph, an airport at
roughly the distance the kit's fixtures already assume, and street names that stay inside the bundled
Latin face (§1.2e). Taken under delegation — "some neutral European city" — so it is revocable by
this item's own measurements rather than by taste.

- **Compact graph wins.** The demo's value is that matching and routing answer quickly on a laptop,
  not that the map is famous.
- The rejected alternative is deferring it until the clients exist. B-01's spike needs a real
  `city.pmtiles` to have rendered anything at all, and a spike against a blank style proves nothing.
- Also produces the glyph PBFs: both style documents point at a `glyphs` endpoint that does not
  exist and say in their own metadata that the PBFs must be generated from Source Sans 3, with
  `["Noto Sans Regular"]` as the interim.

## What it turned out to be

**The city stands, and it is small.** One `city.pmtiles` of **17 415 682 bytes (16.6 MiB)**, a
routing graph that imports in **under four seconds**, and two glyph stacks of 1.2 MB. Nothing here
comes near the sizes that would have reopened the choice.

Everything below is produced by [`map/city_tiles.sh`](../../map/city_tiles.sh) on the Linux box —
`wsl-run 'bash map/city_tiles.sh'` — into `build/city`, which is git-ignored on purpose: B-07 is
what carries the archive to bochka, and a binary in the tree would be a copy nobody re-derives.

**The two style documents are now in the tree, in `map/`, and that is a deliberate exception.** The
brief and the handoff stay outside it because they are evidence; a MapLibre style is not evidence,
it is an asset the clients ship and the build reads — the layer filter above is computed from these
two files on every run, and the preview draws them. They are byte-identical copies of the inputs;
when the designer's version moves, this one is replaced rather than edited.

- ~~AC: one `city.pmtiles`, its size recorded, and both style documents rendering against it.~~
  Done, with both numbers, because there are two: **17 415 682 bytes** with the layer set the styles
  actually draw, **21 897 726 bytes** with the full OpenMapTiles schema. 810 tiles, 826 297
  features, z0–14, planetiler v0.10.2. Both styles were then drawn against it — see below.
- ~~AC: a GraphHopper import of the same extract, with the import time recorded.~~ Done:
  **3.56 s, 3.70 s, 3.84 s, 3.94 s, 4.07 s** over five runs on the box (20 cores, 23 GB), 1.3 GB
  peak RSS, GraphHopper 11.0 with the stock car profile. The graph is **98 566 nodes, 110 853
  edges**, 13 677 517 bytes on disk, bounding 14.221–14.827 E, 45.867–46.264 N.
- ~~AC: the fixture data re-checked against real street names from the extract.~~ Done, and it
  found three wrong strings — below.
- ~~AC: if the extract or the archive comes out large, or the GraphHopper import slow, the number is
  recorded and the city is reconsidered here rather than lived with.~~ Nothing to reconsider. A
  16.6 MiB archive whose biggest tile is 124 kB gzipped is a demo asset, not a download.

### The extract is BBBike's, because Geofabrik does not answer

`download.geofabrik.de` serves `slovenia-latest.osm.pbf.md5` in milliseconds and will not serve the
`.pbf` beside it: the TLS handshake completes, the GET goes out, and nothing comes back — four
attempts from the Linux box (plain, ranged, and with a user agent), and from the mac one redirect
followed by the same silence, 2026-09-01. So the source is BBBike's ready-made Ljubljana extract,
**40 966 215 bytes, md5 `345264d2ce07cf77ddb7d2ea49f0b9d2`**, verified against the publisher's own
`CHECKSUM.txt` rather than pinned in the script, because BBBike regenerates it weekly.

Its bounding box is stated in `Ljubljana.poly`: **14.27–14.77 E, 45.90–46.25 N**. That is the one
thing worth checking before accepting somebody else's extract, and it passes for the reason the
fixtures need — Brnik airport sits at 46.23 N, inside it by two hundredths of a degree. The city and
the airport are in one file, so "the same extract" in the AC above is literally one file.

### A fifth of the archive was drawing nothing

The first build carried the whole OpenMapTiles schema and came out at 20.9 MiB, and its biggest tile
was 241 kB gzipped — **188 kB of which was `poi`**, a layer both styles switch off at every zoom
("POI icons are off at every zoom", in the styles' own metadata). Filtering the build to the seven
source-layers the styles name — `building`, `landcover`, `park`, `place`, `transportation`,
`transportation_name`, `water` — takes the archive to 16.6 MiB and the biggest tile to **124 kB**,
which is the number B-07 pays on a ranged read.

The list is not typed into the script: it is read out of `map/shashki-map-*.json` at build time. A
style that starts drawing a layer therefore rebuilds with it, and one that stops stops paying for it
— the alternative is a constant in two places that agree until the day they do not.

### The styles ask for a face Source Sans 3 does not have

`label-street` wants `["Source Sans 3 Light"]` and `label-place` wants
`["Source Sans 3 SemiLight"]`. Source Sans 3 ships ExtraLight, Light, Regular, Medium, Semibold,
Bold and Black — **there is no SemiLight**. The name is not a typo: it is the Metro ramp's, where
Selawik does have one (kvadrant bundles `selawik_light.ttf` *and* `selawik_semilight.ttf`), and
kvadrant numbers those two steps `KvadrantWeights.Light = W200` and `.SemiLight = W300`.

So the stacks keep the names the styles use and are cut from the faces at those weights —
**ExtraLight for "Source Sans 3 Light", Light for "Source Sans 3 SemiLight"** — which puts the map's
labels at the same two weights as the UI's text. The literal reading (the family's own Light at 300,
and a 350 instance cut from the variable font for SemiLight) would need a synthesised face to answer
a name no font has; this one needs nothing and preserves the ramp's order. Recorded here because it
is a decision, not a lookup.

512 range files, **1 180 298 bytes** across the two stacks, generated by fontnik in a `node:20`
container — the build box has no node, and this is the only step that wants one.

### Both styles render, and the picture had to be taken from inside the page

`map/preview/` is the check: a byte-range static server and one page that loads the vendored style
document, points it at the local archive and glyph directory, and draws it with MapLibre.
`shashki-map-dark.json` at z15 over the centre, `shashki-map-light.json` at the same place, and the
dark one at z11 with the real route on it — all three drawn, no style errors, 943 features in the
dark z15 frame. Street labels curve along the streets and carry their diacritics (*šubičeva ulica*,
*čopova ulica*, *križevniška ulica*), which is the glyph PBFs answering; the route's `ahead` half is
the rider accent `#1BA1E2` over the basemap, which is the accent having no competition, as intended.

A screenshot taken from outside the page comes back **black while the map is fully drawn** — a
hidden browser pane never composites, and `queryRenderedFeatures()` reports hundreds of features on
a canvas that photographs as background colour. The page saves its own canvas instead
(`preserveDrawingBuffer`, `toBlob`, POST back to the server). Anyone re-running this check should
know that the blank picture is the harness, not the archive.

### The fixtures named a house number that does not exist

The kit's `Slovenska cesta 14` is not an address: Ljubljana's Slovenska cesta carries 1, 3, 5, 6, 7,
9A, 9B, 10, 11, 15, 19… and no 14. (There is a Slovenska cesta 14 in the extract — in Horjul, 20 km
west, which is worse than none.) The fixtures now use **Slovenska cesta 15**, and both distances are
the router's rather than the kit's illustration:

| Fixture string | Was | Now | Where the number comes from |
|---|---|---|---|
| pickup | `Slovenska cesta 14` | `Slovenska cesta 15` | an address in the extract |
| pickup meta | `2.1 km · 4 min from you` | `1.8 km · 3 min from you` | 1 843 m, 176 s — station → pickup |
| dropoff meta | `18.4 km · 26 min` | `26.3 km · 20 min` | 26 259 m, 1 227 s — pickup → Brnik terminal B |

The dropoff is the one that mattered: the kit assumed an airport at 18.4 km and Ljubljana's is at
26.3, which research §3 predicted from the map and this measured on the graph. `Airport, terminal B`
survives as written, because OSM calls the building **Brnik terminal B** and the fixture is a UI
label, not a place name.

### §1.2e was right and named too few characters

Every distinct street name in the extract — **3 629 of them** — was collected and its alphabet
compared against the face the UI actually bundles. The non-ASCII characters are
**ć Č č ř Š š Ž ž** and an en dash, not the `č ž š` §1.2e names: `ř` arrives with *Dvořákova*, `ć`
with a handful of surnames. All of them, and every other character in the set, are in
`selawik_light.ttf` and `selawik_semilight.ttf` (348 glyphs each) — **nothing falls through to a
host font**, which is what §1.2e claimed and what [B-05](B-05-glyph-coverage-guard.md) will guard
per-string.

The type-ramp golden now spends its `rowEmphasis` line on **Miklošičeva cesta 4** — also a real
address — so at least one recorded picture exercises the diacritics rather than asserting them.

- Anchors: [`map/city_tiles.sh`](../../map/city_tiles.sh),
  [`map/preview/index.html`](../../map/preview/index.html),
  [`map/shashki-map-dark.json`](../../map/shashki-map-dark.json),
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/ComponentFixtures.kt`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/SkeletonFixtures.kt`
