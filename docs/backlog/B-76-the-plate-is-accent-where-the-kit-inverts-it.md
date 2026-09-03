---
id: B-76
title: "R6's plate is an accent chip; the kit inverts it — and the card says the trip's length, not the driver's"
status: open
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-76 — R6's plate is an accent chip; the kit inverts it — and the card says the trip's length, not the driver's

Live R6: `on its way · 35 min · 22.8 km`, then `Ivan Sokolov / Skoda Octavia · white`, then
`A 123 BC` on a cyan surface. The kit's DriverCard: photo, name at 24, car, `4.92 · 1 284 trips`, the
plate **as a plate — contrast background, SemiBold, wide tracking, "the only inverted element on a
rider screen"** — and `3 min / to pickup · Lenina st, 14` in the accent.

- **The accent on the plate spends the screen's one accent surface on the wrong thing.** The kit
  gives it to the minutes-to-pickup figure; the plate is white on black so it is found first without
  being the accent.
- **`35 min · 22.8 km` is the journey, not the wait.** Before pickup the number a rider watches is how
  far the car is, which the driver's position on the socket answers (B-54) and the map already draws.
- The photo and the trips count are wire gaps: `DriverView` has neither, and inventing a photo is the
  fabrication B-47 refused elsewhere. The rating exists (B-63) and is not shown.
- Deliberately **not** covered: R6·a *driver cancelled* — see B-80.

- AC: the plate is drawn inverted, not in the accent; before pickup the header figure is the time to
  the rider, in the accent; the driver's rating is on the card.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderTrip.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/DriverCard.kt`
