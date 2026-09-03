---
id: B-76
title: "R6's plate is an accent chip; the kit inverts it — and the card says the trip's length, not the driver's"
status: done
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

## What it turned out to be

**The accent was on the wrong element and the figure was the wrong number, and both are one
screen's worth of change.** The plate is inverted now — the foreground as its ground, the
background as its ink, SemiBold, 0.06 em — the kit's "only inverted element on a rider screen".
The figure while the car is on its way is the minutes to it, in the accent, with the kilometres
beside it: `RideView.leg`, which B-75 put on the ride for exactly this reader. When the server has
no road for the driver yet the words stand in, as they did before.

**The rating was already there.** The row draws the driver record's average since B-63; on a stand
with nobody rated it reads `—`, which the sweep saw and this item mistook for an absence.

**Not built, and named in the screen document:** the photo and the trips count. The wire carries
neither, and a photo invented for a card is the fabrication B-47 refused for a licence.

Two goldens added — R6 on both themes, `rider car on its way` — and the four R7 goldens re-recorded
for the plate; all six looked at.
