---
id: B-70
title: "The driver finishes a trip and is shown nothing: D5 does not exist"
status: open
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-70 — The driver finishes a trip and is shown nothing: D5 does not exist

Live on the desktop driver against the stand (2026-09-03): *finish* is tapped, the trip screen pops,
and the next frame is D2 *waiting*. The kit's **D5 — trip complete** is a whole screen: `+420 ₽` at
54 in the accent, `card · settled tonight · today 4 700 ₽`, then `fare 470 ₽ / service fee 12% −50 ₽
/ 26 min · 18.4 km`, *rate anna*, and "Next offer arrives automatically. You stay online." Its own
note is the design's argument: **the figure is what he earned, not what the passenger paid, and the
fee is shown, never hidden.**

- The number exists. `PayoutRepository` holds the driver's share and D6 sums it; what is missing is
  the moment it is shown for the ride that just produced it.
- The rider's R8 is the mirror of this screen and it is built; a product that shows one side its
  money and not the other is telling half the story of the settlement.
- Deliberately **not** covered: rating the passenger. Nothing in the protocol carries a rider rating
  and inventing one for the screen would be a second rating system.

- AC: after `COMPLETED` the driver sees the payout for that ride, the fare it came from and the
  platform's cut, before returning to the shift — and returns to it on one tap or after a delay,
  as the kit says.
- AC: a golden of D5 on both themes, and the figure on it is the payout row's, not the fare.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/App.kt`,
  `docs/screens/screen-driver-assigned-ride.md`
