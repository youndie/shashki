---
id: B-80
title: "Four state branches the kit draws and the product cannot reach"
status: open
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
