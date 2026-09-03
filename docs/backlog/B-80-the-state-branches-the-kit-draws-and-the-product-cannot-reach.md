---
id: B-80
title: "Four state branches the kit draws and the product cannot reach"
status: done
priority: P3
size: M
stage: stage-6-what-running-it-said
---

# B-80 — Four state branches the kit draws and the product cannot reach

The flows document wires seven state branches into the two applications. The desktop sweep reached
three (R5·a *no cars*, R9·c *empty*, R10 *cancel*). Four have no path in the product:

| Branch | What the kit says | What the product has |
|---|---|---|
| R6·a driver cancelled | "Ivan is not coming. Nothing was charged." — toast first, page after 10 s | no way for a driver to cancel an accepted ride |
| R7·a gps lost | full-width band, map at 40 %, `— min · estimate paused · fare held` | the socket's silence is not surfaced to the rider |
| R8·a payment failed | red headline, `retry card / add another card / pay cash` | the gateway is in-memory and never declines |
| D4·a passenger cancelled | `+99 ₽ compensation · back online after 8 s` | the settlement charges the fee (B-37) and the driver's screen returns to *waiting* with no word |

- Two of these are one line each on a screen that exists (R7·a and D4·a); two need a transition the
  server does not offer (a driver cancelling; a declined capture).
- Deliberately **not** covered: R1–R3 and D0 sign-in/home/search, which the desktop build skips by
  design and the browser build draws (B-26, B-52).

- AC: each of the four either has a path in the product with a golden, or is recorded in the
  research as a branch this reference does not build, with the reason.
- Anchors: `docs/research/research-architecture.md`, `shared-ui/src/desktopTest/snapshots/`

## What it turned out to be

**Two built, two recorded, and the line between them is whether the server has the event.**

- **R7·a gps lost** is a band on R7 after thirty seconds of a silent socket — the kit's full-width
  band at the join, never a card, the map dimmed under it — with the seconds on it, and it comes
  down on the next position. Half a minute rather than the first silence: ten seconds in a tunnel
  is not a lost car. `TripViewModelTest` moves its own clock forty seconds and reads the number.
- **D4·a passenger cancelled** is D5 with another first line. The settlement already charged the
  fee and paid the driver its share (B-37); the trip screen already left on any terminal status
  (B-70); what was missing was the summary saying *why* — `TripSummaryView.cancelled`, and
  `cancellation fee` where `fare` would be. `SettlementTest` reads the compensation off the wire
  after a rider cancels on an assigned car.
- **R6·a driver cancelled and R8·a payment failed are recorded in the research, §5.3**, each with
  the reason: the first is a policy before it is a screen — who is compensated, whether the cascade
  resumes — and the second is a decline the in-memory gateway cannot produce, so a screen for it
  would be a golden of a fiction.

One golden added — R7·a on both themes — and looked at.
