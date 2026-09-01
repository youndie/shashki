---
id: B-24
title: "Motorways carry ref and not name, so the styles label none of them"
status: open
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
`transportation_name`, of which 10 783 have `name` or `name:latin` and **654 have only `ref`**. Every
one of the 654 is `class=motorway`. Found while decoding a tile for
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

- AC: both documents coalesce through `ref`, and a tile whose only named road is a motorway renders
  a label.
- AC: the number above is re-measured after the change — 0 of 11 437 unlabelled, not 654.
- Anchors: `map/shashki-map-dark.json`, `map/shashki-map-light.json`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/map/MvtDecoderTest.kt`
