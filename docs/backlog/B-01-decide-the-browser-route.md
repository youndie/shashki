---
id: B-01
title: "Decide how the clients reach a browser, and write the choice down"
status: open
priority: P0
size: L
stage: stage-0-unknowns
---

# B-01 — Decide how the clients reach a browser, and write the choice down

The brief makes both clients Compose Multiplatform → wasm and puts MapLibre Compose under the map.
Research §1.3 found that `org.maplibre.compose:maplibre-compose` 0.15.0 publishes android, jvm,
iosArm64, iosSimulatorArm64 and **js** — no `wasmJs` — that the project's own documentation says
Kotlin/Wasm is not supported, and that the upstream work (PR #1081) is pinned to an unreleased
Compose build. Kotlin/JS is not a way round it: neither `kvadrant-core` nor `kompot-client` has a
`js` target.

- **Build the comparison, not the argument.** Four routes are on the table — desktop-first on
  published artefacts; wasm on the pinned Compose dev build; wasm with maplibre-gl-js in the DOM and
  Compose over it; and drawing the map ourselves in Compose from the tiles. Each is prototyped
  against the *same* two screens, `RiderClassPicker` and `RiderTripInProgress`, because those put the
  most Compose on top of the most map.
- **Route 4 is judged on a different axis, and that is the point.** Research §1.8 measured it: the
  two style documents use 13 layers, seven paint properties, seven layout properties and seven
  expression operators between them, with no sprites, no icons and no halos; and the hardest missing
  piece, a label following a curved street, is reachable through `PathMeasure.getRSXform` and
  `TextBlob.makeFromRSXform`, both verified present in the **Kotlin/Wasm** build of skiko. It closes
  four open items — glyph PBFs, the compositing hole, the pmtiles protocol handler, and a map that
  can never appear in a golden — and opens one: how much tile pipeline we own.
- The rejected alternative is choosing now from the descriptions. Routes 1 and 2 differ by a Compose
  release date nobody here controls, route 3's cost is a layout fact a prototype produces and an
  opinion does not, and route 4 trades a dependency risk for a quantity of work, which only a
  prototype prices.
- **WorldWind Kotlin is checked and it is route 3.** `earth.worldwind:worldwind` (Apache-2.0,
  active) has a Compose module with a `wasmJs` target; §1.8c read its source. It inserts its own
  full-window WebGL canvas behind Compose's transparent one — its KDoc says Skia-backed Compose/Web
  "cannot embed WorldWind's own WebGL canvas inside that surface" — the map cannot be a sized
  element, and pointer events are all-or-nothing because Compose controls are not DOM elements. It
  belongs in the route-3 column, better packaged. Its 30-file MVT package is, separately, the best
  available size estimate for route 4's decode-and-label half, and Apache-2.0.
- **Unblocked from [B-06](B-06-city-extract-and-tiles.md), deliberately.** The prototypes need *a*
  `city.pmtiles` with real roads, and a Protomaps bounding-box extract of Ljubljana is one file and
  one minute; B-06 is the archive the demo ships plus the graph the router imports, and it can land
  after the route is chosen. A P0 unknown does not wait on a P1 build step it can borrow from.
- Deliberately **not** covered: shipping either client. This item ends with a decision recorded in
  research §2 D1 and the map interface committed.

- AC: `docs/research/research-architecture.md` D1 names one route, with the measurement that decided
  it and what the other two cost.
- AC: the map lives behind one interface with a platform implementation per target, so the decision
  is a module swap.
- AC: each prototype renders the city from `city.pmtiles`, not a blank style — this is also Risk 5's
  answer.
- AC: route 4's prototype renders at least the road layers and one street label along a curve, so the
  estimate for the rest is made against something that ran.
- AC: whether WorldWind's `MvtMapboxStyleLoader` actually loads shashki's two style documents is
  settled by running it once — its KDoc and its body disagree (§1.8d), so neither is quotable.
- Anchors: `kvadrant-ui/kvadrant-core/build.gradle.kts`, `kompot/kompot-client/build.gradle.kts`
