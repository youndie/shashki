---
id: B-77
title: "R7 does not say when you arrive or how far is left, and the travelled road stays lit"
status: open
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-77 — R7 does not say when you arrive or how far is left, and the travelled road stays lit

Live R7: `airport · 35 min · 22.8 km`, the driver row, the plate, `call the driver ···`. The kit's R7:
`18 min / arriving 20:06 · 11.2 km left / 420 ₽ / Airport, terminal B · change`, and *"travelled route
drops to 25 % white, ahead stays accent · progress is colour, not thickness"*.

- The two numbers that change during a trip — minutes left and kilometres left — are the two the
  screen does not show; it repeats the quote. The car's position is on the socket and the route
  geometry is on the ride (B-23), so both are subtraction.
- The route is one colour from end to end. `MapScene` already has the travelled/ahead split (B-25's
  two phases) and the trip does not feed it.
- Deliberately **not** covered: R7·a *gps lost* — see B-80.

- AC: R7's figure is the minutes left, its meta the arrival clock and distance left, the fare shown
  once; the road behind the car is drawn at the kit's 25 % white.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderTrip.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/TripViewModel.kt`
