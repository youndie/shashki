---
id: B-77
title: "R7 does not say when you arrive or how far is left, and the travelled road stays lit"
status: done
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

## What it turned out to be

**The two numbers that change during a trip were one field away.** `RideView.leg` (B-75) is the
road from the car's last position to the drop-off once the trip is running; R7's figure is its
minutes and the meta its kilometres, with `arriving 20:06` beside them — the view model's, from the
rider's own clock, which is the one they will compare it with. The fare is shown once, under the
figure, where the kit's `420 ₽` is. Without a leg the screen says what it said before.

**The road behind the car goes to 25 % white and the road ahead stays the accent.** `MapScene` had
the two phases since B-25 and the trip never fed them: the whole road was the accent from end to
end. The split is at the road vertex nearest the car, on every position the socket reports, and
only once the trip is running — before pickup the car is not on this road at all. Nearest vertex
rather than a projection onto the segment, because the router's vertices and a phone's GPS are both
a few metres coarse and the car marker is the joint either way.

`TripViewModelTest` holds the split with a three-point road and the car at its middle vertex, and
the clock with a `now` the test holds still. Four goldens re-recorded — R7 on both themes, with and
without a scene — and looked at.
