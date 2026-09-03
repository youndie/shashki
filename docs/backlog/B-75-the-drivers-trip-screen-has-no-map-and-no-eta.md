---
id: B-75
title: "D4 has no map and no time to the pickup"
status: done
priority: P3
size: M
stage: stage-6-what-running-it-said
---

# B-75 — D4 has no map and no time to the pickup

Live D4 is a status word in amber (`on the way` → `at the pickup` → `on the trip`), the fare at 54,
two coordinate lines and one bar whose label changes — which is exactly the kit's *one bar carries
the whole chain*. What is above the bar is not: the kit's D4 is a map with the car and the route,
`4 min / 2.1 km to pickup` at 54, the pickup address and `Anna · 4.98 · comfort`.

- The rider's R6/R7 draw the same map from the same tiles in the same module; the driver's screen
  does not, and a driver is the person who needs the road most.
- The fare at 54 on a driving screen is the kit's *figure* slot spent on the wrong number: the kit
  puts the minutes there and the fare nowhere.
- Deliberately **not** covered: the passenger's rating, for B-74's reason.

- AC: D4 shows the map with the route to the pickup and then to the drop-off, and the figure is the
  time to the next point rather than the fare.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripScreen.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/`

## What it turned out to be

**One field on the ride, and three screens can use it.** `RideView.leg` is the road from where the
assigned driver last said they were to the next point — the pickup until they have arrived, the
drop-off after — routed on the server from the position the driver's socket reported, at the moment
of the read. D4 puts its minutes at 54 with `2.1 km to the pickup` beside them; R6 and R7 (B-76,
B-77) are the other two readers and were the reason to put it on the ride rather than on a driver
route. `null` is honest in four cases and the field's own note lists them.

**The map is the rider's, moved rather than rebuilt.** `MapPane`, `MapScene`, the pins and the
route line are `:shared-ui`'s; the driver bundle needed the surface bound and a `tilesUrl` to bind
it with — `SHASHKI_TILES` on the desktop, the page's `tilesUrl` on the web, the same two the rider
reads. The car is this driver's own position from the same `PositionFixes` the shift reports up the
socket, and the camera follows it: a driver looks at where they are.

**The road is fetched once per leg and not per fix.** A road re-routed every second is a graph
search for a line that hardly moves; the car marker is what shows where the driver actually is, and
the leg's start is the configured point on purpose. When the leg changes — arrived — the road is
asked again, from the pickup to the drop-off.

**The fare moved to a line.** The kit puts nothing where this screen used to put the fare at 54; the
minutes take the slot and the fare stays, small, for a driver on a demo stand who wants to see it.
While the server has no road for the driver — no position yet — the fare keeps the slot, labelled.

`RideRoutesTest` asserts the leg off the wire after the accept: to the pickup, with a number. Four
goldens re-recorded — D4 before and during the trip, both themes — with the placeholder map every
map fixture in this repository uses.
