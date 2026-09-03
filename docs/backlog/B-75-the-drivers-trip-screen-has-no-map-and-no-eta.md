---
id: B-75
title: "D4 has no map and no time to the pickup"
status: open
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
