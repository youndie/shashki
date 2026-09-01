---
id: B-25
title: "The rider's trip-in-progress screen, on the map that D1 chose"
status: open
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

- AC: `RiderTripInProgress` exists in `shared-ui` and is a golden, in both palettes, with the map
  under it rather than a placeholder.
- AC: the route is drawn in both phases, visibly different, and the difference comes from the same
  property the style documents filter on.
- AC: the car and the two pins are drawn from `MapScene` — a screen built from an empty scene shows
  an empty map, not a screen that quietly hard-codes its own contents.
- AC: a fixture whose scene names a phase the styles do not know fails rather than drawing nothing,
  because a route leg silently missing is the defect this screen is most likely to ship.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/MapScene.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/CanvasMapSurface.kt`
