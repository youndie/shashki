---
id: B-68
title: "D4 draws in_progress where every other state is a word"
status: done
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

## What it turned out to be

**A word per state, and the guard is the `else`.** `RideStatus.asWord()` names the five a driver
meets — *assigned*, *on the way*, *at the pickup*, *on the trip*, *finished* — and its fallback
replaces underscores with spaces rather than printing an identifier, so a state added to the wire
tomorrow reaches a person as prose while somebody decides what it should say.

**What the test holds is not the five strings.** Those are a design decision and may change; the
assertion is that **no state reaches the screen with an underscore in it**, which is exactly what the
next `RideStatus` would otherwise do. Dropping the word for `IN_PROGRESS` fails it.

**And it now has a picture.** The only golden of this screen showed `ASSIGNED`, where the word and
the identifier happen to be the same string — so the one state that was wrong had never been
photographed. `assigned ride on the trip` is the fixture, in both themes, and reads *on the trip*
over *finish*.

The button's own words were already right: `asAction` has said "on my way", "I am here", "start the
trip" and "finish" since the screen existed. It was the header that printed the enum.
