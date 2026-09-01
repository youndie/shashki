---
id: B-01
title: "Decide how the clients reach a browser, and write the choice down"
status: wip
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
- ~~AC: the map lives behind one interface with a platform implementation per target, so the decision
  is a module swap.~~ **Done, 2026-09-02.** `MapSurface` in `shared-ui`, with `MapScene` carrying
  exactly what the kit draws — a camera, a route in the two phases the styles filter on, cars, two
  pins — and nothing a renderer might additionally offer. `LocalMapSurface` has no default, so a
  screen outside an application that bound one fails on first read rather than rendering a
  map-less screen a golden would pass.
- AC: each prototype renders the city from `city.pmtiles`, not a blank style — this is also Risk 5's
  answer.
- AC: route 4's prototype renders at least the road layers and one street label along a curve, so the
  estimate for the rest is made against something that ran.
- ~~AC: whether WorldWind's `MvtMapboxStyleLoader` actually loads shashki's two style documents is
  settled by running it once.~~ **Done, 2026-09-02: eleven of thirteen layers.** Fed the whole
  document it throws; fed one layer at a time, every `fill` and `line` layer loads and both `symbol`
  layers fail — on `text-field`, which the styles write as `["downcase", ["coalesce", …]]` and the
  loader takes as a plain `"{name}"`. The `interpolate` worry was unfounded: road widths and the
  rail's dash array go through. Research §1.8d has it, with the second finding: the failure is a bare
  `IllegalArgumentException` naming a *type*, not the documented `MvtStyleParseException` naming the
  offending node.
- Anchors: `kvadrant-ui/kvadrant-core/build.gradle.kts`, `kompot/kompot-client/build.gradle.kts`

## Progress

**2026-09-02 — the two cheap facts first, because either could have collapsed the route space.**

- The upstream gate has **not** moved: the newest non-prerelease Compose is 1.12.0 (2026-08-25) and
  both maplibre-compose tickets still carry `blocked-upstream`. Routes 2 and 3 through the official
  wrapper stay unavailable on released artefacts, so nothing about this choice is settled by waiting
  another week.
- WorldWind's style loader is settled (above). It moves route 3's cost down a little — the basemap
  parses but for one property — and does not touch the compositing objection, which is what §1.8c
  disqualified it on.

**2026-09-02, second pass — the seam and the first of the two screens.**

`MapSurface` exists and `RiderClassPicker` is built against it, which turns one of the four routes'
differences from an argument into a measurement waiting to be taken: **the kit gives the map 360 of
the 844 dp and hangs the panel below it**, so on this screen the map is a *sized element*. Routes 1
and 3 draw outside Compose's canvas and can only approximate a rectangle inside it — WorldWind's
binding says in its own KDoc that its canvas is always full-window — while route 4 is a composable
like any other. That is now a thing a prototype can be held against rather than a sentence.

`PlaceholderMapSurface` fills the hole meanwhile, and does it honestly: it paints the style
documents' own background colour and says no renderer is bound, so `screens_rider_class_picker` is
visibly a screen 360 dp short of one rather than a screen whose map failed. When a renderer lands the
image changes, and that change is the evidence.

Two more of the kit's twenty-four icons transcribed (`check`, `card`), and a second sighting of a
finding: **the kit's bottom bars are not `KvadrantAppBar`.** The library centres its buttons and puts
labels underneath; R4 and D3 are an action row — ring, label, overflow dots. The ring itself is the
library's `KvadrantAppBarButton`, which is where the 36 dp visual and the 48 dp target come from.

Still to do: the four prototypes, `RiderTripInProgress`, and D1's written decision. `city.pmtiles`
exists (B-06), so they get the real archive rather than a bounding-box stand-in.

**2026-09-02, third pass — route 4's decoder, against the city's own tiles.**

The half of route 4 that is pure logic is built and tested: a minimal protobuf reader and an MVT
decoder in `shared-ui/.../map/tiles`, about 300 lines between them, no dependency added.
`kotlinx-serialization-protobuf` publishes a Wasm target and would have done it — but it wants a
schema, and MVT's whole wire format is four wire types and one packed repeated field. WorldWind
reached the same conclusion and ships its own `ProtobufReader`; two independent implementations
choosing the same thing is worth recording.

The test runs against a real tile lifted out of `city.pmtiles` — `z14/8850/5815`, 4 068 bytes, the
smallest tile in the archive that carries both roads and a street name — and **every expected number
came from a second reader written in Python before the test existed**. A geometry decoder is exactly
the kind of code that produces confidently wrong output, and a test whose expectations come from
running it would agree with it rather than check it.

Reading the pmtiles container was part of the same work and is now understood rather than assumed:
v3, one root directory and no leaves, gzip inside and out, 810 tiles, tile type MVT, Hilbert tile
ids. That is the second half of route 4's "tiling" cost from §1.8b, and it came out small.

**It found a defect nobody was looking for** — the styles label no motorway at all, 654 roads of
11 437 — which is [B-24](B-24-motorways-carry-ref-not-name.md) and research §1.8e. That is what
prototypes are for, and it is a point in route 4's column that has nothing to do with rendering:
owning the decode meant reading the data, and reading the data found it.

Still to do: the Compose Canvas half of the prototype (roads drawn, one label on a curve), the
routes 1–3 prototypes, `RiderTripInProgress`, and D1's decision.
