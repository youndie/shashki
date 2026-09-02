---
id: B-68
title: "D4 draws in_progress where every other state is a word"
status: open
priority: P3
size: XS
stage: stage-6-what-running-it-said
---

# B-68 — D4 draws in_progress where every other state is a word

The driver's assigned-ride screen labels the trip with the ride's status, and the label is the enum's
own name: `assigned`, `arriving`, `arrived` read as words by luck, and `in_progress` reads as an
identifier — an underscore on a screen whose type ramp is the kit's and whose headings are lower-case
prose.

- **A status is a wire value and a label is a sentence**, and the two coincide for three of the four
  by accident. `RideStatus.name.lowercase()` is the client asserting they always will; the next state
  anybody adds — `awaiting_payment`, say — arrives on the screen with its underscore.
- The rejected alternative is renaming the enum. The wire is not the place to solve a typography
  problem, and `IN_PROGRESS` is the right name for the value.
- Deliberately **not** covered: translating them. There is one language on these screens.

- AC: the four states read as the kit's words — *on the way*, *arriving*, *arrived*, *on the trip* or
  whatever the kit's D4 calls them — and a golden shows the one that was wrong.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripScreen.kt`,
  `docs/screens/screen-driver-assigned-ride.md`
