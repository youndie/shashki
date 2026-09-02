---
id: B-25
title: "The rider's trip-in-progress screen, on the map that D1 chose"
status: done
priority: P1
size: M
stage: stage-3-surface
---

# B-25 — The rider's trip-in-progress screen, on the map that D1 chose

Split out of [B-01](B-01-decide-the-browser-route.md) when that item closed. It was there as the
second of two screens to hold four map prototypes against; three of the four turned out to have
nothing to prototype, so the screen stopped being a measuring device and went back to being a screen
the product needs — and one whose content the map decision now fixes rather than questions.

It is the screen with the most map on it: the route drawn in its two phases, the car moving along it,
two pins, and a panel that changes as the ride does. R4's class picker gives the map 360 of 844 dp;
this one gives it more, and everything the kit draws sits over it.

- **The renderer is settled, so this is a drawing task rather than an open question.**
  [D1](../research/research-architecture.md#d1-the-browser-is-a-decision-with-a-date-not-a-precondition)
  chose route 4, `MapScene` already carries exactly what the kit draws — a camera, a route in the two
  phases the styles filter on, cars and two pins — and `CanvasMapSurface` draws tiles. What is
  missing is the scene's own contents: nothing yet draws the route line, the car or the pins.
- **The route has two phases and the styles filter on them.** The style documents carry a `route`
  GeoJSON source with the pickup leg and the trip leg distinguished; drawing both the same colour
  would be a screen that looks right and is wrong.
- Deliberately **not** covered: gestures and camera animation. A trip screen that follows the car is
  a camera question, and camera is the largest single line item in route 4's cost (§1.8b); it gets
  its own item once there is something to move over.

- ~~AC: `RiderTripInProgress` exists in `shared-ui` and is a golden, in both palettes, with the map
  under it rather than a placeholder.~~ **Done, 2026-09-02.** `screens_rider_trip_in_progress` and
  its light twin, with `CanvasMapSurface` under them.
- ~~AC: the route is drawn in both phases, visibly different, and the difference comes from the same
  property the style documents filter on.~~ **Done, and the "same property" is checked rather than
  claimed.** `scripts/style_contract.py` reads both documents' route filters and `RouteLine.PHASES`
  out of the Kotlin, and fails if they disagree — in either direction, both controlled.
- ~~AC: the car and the two pins are drawn from `MapScene` — a screen built from an empty scene
  shows an empty map, not a screen that quietly hard-codes its own contents.~~ **Done, and the empty
  scene is its own golden**: `screens_rider_trip_on_an_empty_scene` is the same screen with nothing
  on the map, so a renderer drawing its fixture regardless would be visible as two identical images.
- ~~AC: a fixture whose scene names a phase the styles do not know fails rather than drawing
  nothing~~ **— not written, because the type makes it unrepresentable.** `RouteLine` is two fields,
  not a map keyed by a phase name, so there is no way to name a phase and no run-time failure to
  write. What *can* drift is the documents growing a third phase while the renderer keeps two, and
  nothing in Kotlin would notice — so that is what the guard checks instead.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/MapScene.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/CanvasMapSurface.kt`

## What it turned out to be

**The screen was the small half. The guard on it took the iteration.**

The map half is straightforward and now real: a Web Mercator projection between the scene's
coordinates and the tile's pixels, the route stroked in the two phases with the documents' own
colours and width, and the cars and pins laid over the canvas as composables — because `CarMarker`'s
rule is that markers do not rotate with the map, and a marker painted into the map layer is a marker
that would.

**Two things about the map changed while drawing it.** The tile now *covers* its pane instead of
fitting inside it: fitting left a band of background wherever the pane was not square, and the first
golden showed exactly that under a 390 × 440 map. And the projection had to be shared between the
canvas and the markers, so the pane's size is read once from the layout rather than measured twice.

**The screen's artboard was not read, and the screen says so.** Every other screen here is
transcribed from the kit; this one's drawing was not among the files opened during the research. The
map half is not a guess — phases, colours and width come from the documents — but the *panel* is
built from the recorded composition rules and is provisional. Recording that in the KDoc is the point
of §1.1's rule: an invented layout presented as transcribed is worse than an admitted gap.

**The guard failed to fail, and that is the finding.** The phase check was written first as a Kotlin
test reading `map/*.json` directly. It was correct — with `--rerun-tasks` a deliberately broken
document failed it by name. Without that flag the same broken document **passed**: the task came back
`FROM-CACHE`, because Gradle cannot see a file a test opens by hand. Declaring `map/` an input with
`tasks.withType<Test>` did not change the outcome, and a probe showed the tail of
`shared-ui/build.gradle.kts` not executing at all — with and without the configuration cache, with
the box verified to hold the edited file in the same invocation. That symptom is reproducible and its
mechanism is not established, so it is not being written down as one.

What is written down is the decision it forced: the guard moved to `scripts/style_contract.py` and
into `make check`, which has no notion of being up to date. For a guard, running every time is worth
more than living beside the code it guards. Both directions are controlled — a changed document
fails, and a changed `RouteLine.PHASES` fails.

**And it pinned a second finding on the way.** Both documents declare a `cars` GeoJSON source that
**no layer draws**. That is not a defect — the kit draws cars as markers, which is what this screen
does — but it is a loose end, and the guard now fails if a layer appears, so whoever adds one is told
that `CanvasMapSurface` has to grow with it.
