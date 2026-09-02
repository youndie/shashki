---
id: B-24
title: "Motorways carry ref and not name, so the styles label none of them"
status: done
priority: P1
size: XS
stage: stage-1-skeleton
---

# B-24 — Motorways carry `ref` and not `name`, so the styles label none of them

Both style documents write the street label as
`["coalesce", ["get", "name:latin"], ["get", "name"]]`. OpenMapTiles labels a motorway by its number
instead: the feature carries `ref` — `A1`, `A2`, `E 61` — and no `name` at all. The expression
therefore evaluates to nothing and the road is drawn with no label on it.

**Measured, not suspected.** Across all 810 tiles of `city.pmtiles`: 11 437 features in
`transportation_name`, of which 10 783 have `name` or `name:latin` and **654 have only `ref`**.
~~Every one of the 654 is `class=motorway`.~~ **Wrong, corrected 2026-09-02:** 194 are motorway; the
rest are secondary 195, tertiary 152, primary 107, path 4, minor 1, primary_construction 1. The count
was right and the characterisation was an impression from the one road the prototype drew. Found
while decoding a tile for
[B-01](B-01-decide-the-browser-route.md)'s route-4 prototype — the single named road in the smallest
tile that has one turned out to be the A2, and it had nothing the style could draw.

- **It is the wrong 5.7 %.** This product's flagship journey is the city to Brnik, and the road that
  carries it is the A2. The one road a rider watches themselves travel along is the one road with no
  name on the map.
- **The fix is one token**: add `["get", "ref"]` as a third branch of the `coalesce`, in both
  documents. `ref` is short, uppercase and reads correctly at the label sizes the styles already set;
  nothing else about the layer changes.
- The rejected alternative is a separate shield layer — the highway-shield treatment real maps use,
  a rounded badge with the number in it. It is what a navigation app would do and it is a new visual
  the kit has not drawn; a plain label is what the kit's `label-street` already specifies.
- **It is a change to a design artefact**, which is why it is an item rather than an edit: the style
  documents came from the designer, and §1.1's rule is that a divergence is raised rather than
  quietly corrected. This one has a measurement attached and an obvious answer, so it should be
  cheap to agree.

- ~~AC: both documents coalesce through `ref`, and a tile whose only named road is a motorway
  renders a label.~~ **Done, 2026-09-02.** Both `label-street` layers now read
  `["downcase", ["coalesce", ["get", "name:latin"], ["get", "name"], ["get", "ref"]]]`;
  `label-place` is left alone, because every one of the archive's 23 604 places carries a name and
  none carries a ref. `map_canvas_tile_dark` draws the A2's number along it.
- ~~AC: the number above is re-measured after the change — 0 of 11 437 unlabelled, not 654.~~
  **Done: 0 of 11 437, in both documents.** Every one of the 654 carries `ref`, so the third branch
  closes all of them rather than most.
- Anchors: `map/shashki-map-dark.json`, `map/shashki-map-light.json`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/map/MvtDecoderTest.kt`

## What it turned out to be

**The fix was the one token the item promised. What it cost was a script, and the script is the
part worth keeping.**

The item's number — 654 — came from a throwaway reader during B-01, and its sentence about *what*
those 654 were came from looking at one road. `map/label_coverage.py` replaces both. It reads the
archive and takes the label keys **out of the style document's own `text-field`**, so it measures the
map rather than what the author remembers the map saying — which is precisely the drift this item
exists to fix, and hard-coding `name:latin` and `name` in the script would have reproduced it one
level down.

Run against the unmodified styles it reproduces 654 exactly. That control is what makes the 0
afterwards mean something; without it, a reader that silently found nothing would look like a fix.

**The item's characterisation was wrong and the count was right.** Not "every one a motorway" but
194 motorway, 195 secondary, 152 tertiary, 107 primary, and a handful of paths. That changes what the
defect *is*: not missing motorway shields, which a map might reasonably do without, but every
numbered road in the city going unlabelled — and a third of them ordinary streets a rider would
expect to read. Research §1.8e is corrected at the point of divergence rather than rewritten.

**And the same measurement found the mistake pointing the other way.** Both documents say
`["downcase", …]`. Route 4's renderer did not, so every label in the map goldens was drawn in the
source's own case — "A2" and "Voglje" where the design says "a2" and "voglje". A renderer that draws
a different string from the one the style specifies produces a golden that certifies the wrong
picture, which is worse than having none. `labelText()` lower-cases now, the three map goldens are
re-recorded, and [B-05](B-05-glyph-coverage-guard.md)'s pinned label list caught the change on the
first run — which is the first time that guard has bitten on something other than its own control.

**One thing deliberately not done.** `downcase` lower-cases the road number too, so the A2 reads
"a2". The item's own argument for `ref` was that it "is short, uppercase and reads correctly", which
the `downcase` wrapper defeats — but the kit's street labels are lower case by design and a number
exempted from that would be the only capital letter on the map. Keeping the one-token fix means the
renderer and the style agree; making the number an exception is a design decision and belongs to
whoever drew the kit, not to this item.
