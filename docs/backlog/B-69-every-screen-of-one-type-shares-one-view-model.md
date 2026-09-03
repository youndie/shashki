---
id: B-69
title: "Every screen of one type shares one view model, so the second ride is shown the first"
status: done
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-69 — Every screen of one type shares one view model, so the second ride is shown the first

Found on the desktop clients against the stand, ordering a second ride in the same window
(2026-09-03). The rider taps *order*, the server creates the ride and offers it to the driver — and
the rider's screen goes straight to R5·a, *no cars nearby*, while the driver is still counting down
from fifteen. The driver accepts, the server answers 200 and marks the ride `ASSIGNED`, and the
driver's screen returns to *waiting*: no trip, no *on my way*. Both clients are wrong about a ride
the server is right about, and both in the same way.

- **`NavDisplay` was left to its defaults, and the default keeps saved state per entry and nothing
  else.** `koinViewModel()` resolves against the nearest `ViewModelStoreOwner`, which with no
  per-entry store is the window's one. So `Matching("ride-2")` is handed the view model built for
  `Matching("ride-1")` — already at *no cars*, polling a ride that has ended — and the driver's
  second `Trip` is handed the first trip's, whose `COMPLETED` pops the screen before it draws.
- **Nothing in `check` could see it.** Every golden photographs a `Content` composable with a state
  handed in; `RiderGraphTest` and `DriverGraphTest` build every view model from the graph, once
  each. The defect is in the composition of two entries of one route, which nothing composed.
- The first ride in a window works, which is why the browser walkthrough two days ago did not find
  it: every screen there was reached once.
- Deliberately **not** covered: view models for screens that are genuinely singletons in a window
  (the picker, the shift). They gain a store of their own too, and lose nothing by it.

- AC: `entryDecorators` on both `NavDisplay`s carry `rememberViewModelStoreNavEntryDecorator()`
  beside the saved-state one, and a second ride in one window reaches R5, R6, R7 and R8.
- AC: a test composes two entries of one route through the application's own decorators and fails
  without the line.
- Anchors: `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/App.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/App.kt`,
  `rider/src/desktopTest/kotlin/io/github/youndie/shashki/rider/NavEntryViewModelTest.kt`

## What it turned out to be

**One line, and the line is `rememberViewModelStoreNavEntryDecorator()`** — beside
`rememberSaveableStateHolderNavEntryDecorator()`, which is what `NavDisplay` had silently been using
alone. The artefact is `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`, pinned to
the 2.11.0-beta01 line this navigation3 already resolves.

**Measured before and after, on the stand, in one window.** Before: the second order went straight
to *no cars nearby* while the driver counted down from thirteen, and the driver's accept — 200 from
the server, ride `ASSIGNED` — left the driver's screen at *waiting*. After: R4 → R5 (*looking for a
car*) → R6 (*on its way*, then *waiting for you*) → R7 → R8 with a rating and a tip → R9 → R9·b with
the tip on it; the driver went *on the way* → *at the pickup* → *on the trip* → *finish* and back to
the shift.

**`NavEntryViewModelTest` composes two `Matching` entries through the application's own decorator
list and asserts two view models.** With the store decorator removed it fails with "the second ride
was handed the first ride's view model" and the same instance on both sides — the control run was
made, not assumed.

**Why nothing had seen it.** Every golden takes a `Content` composable and a state; every graph test
resolves a view model once; the browser walkthrough two days ago reached every screen once. The
defect needs the same route twice in one window, and nothing in `check` navigated at all. The test
above is the first thing in this repository that does.
