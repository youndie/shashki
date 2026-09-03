---
id: B-61
title: "R9's rows carry one address and no date, and R9·b does not exist"
status: open
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-61 — R9's rows carry one address and no date, and R9·b does not exist

Live at `/trips` the rider's history draws rows reading `airport` over `completed · 4.1 km`. The
kit's R9 row is two addresses — `Lenina st, 14` → `Airport, terminal B` — over `28 aug · 19:40 ·
economy`, with the fare and the payment method on the right, month headers in the accent that
"travel at their own rate and wrap round", and a row that opens **R9·b**, the receipt, composed from
`FareBreakdown`.

- **`FareBreakdown` exists and nothing renders it.** It is in the kompot dictionary, it has a
  renderer and a golden, and no screen in the product shows a receipt — which makes it the fourth
  mechanism in this repository written at both ends and joined at neither. The detail screen is what
  gives it a caller, and it is the piece of R9 worth building first.
- **The row's poverty is a data question, not a layout one.** The pickup address, the time and the
  payment method are all on the ride; the row shows one label because that is what
  [B-45](B-45-history-from-the-broker.md) put on the wire. Widening the row means widening the view
  it is built from.
- **The month headers are the one piece of pure kit.** They are the Metro touch a reader recognises,
  and a list that groups by month is also easier to read at ten rides than at three.
- Deliberately **not** covered: the *profile* page of the pivot. The pivot has three items and two
  of them are drawn; profile has no content decided.

- AC: a row shows both ends of the journey, when it happened, and what it cost with what.
- AC: tapping a row opens a receipt drawn by `FareBreakdown` from the server's own lines, and the
  client computes no total.
- AC: months group the list, and an empty month is not drawn.
- Anchors: `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/history/`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Ride.kt`,
  `docs/screens/screen-rider-history.md`

## What is done, and what is blocked (2026-09-03)

**The row and the months are drawn.** A row carries both ends of the journey, when it happened, the
class, the payment method and what it cost; the list is grouped by month with the header in the
accent, and a month with nothing in it is not drawn. `RideView` carries `requestedAtEpochMs` and the
client formats it, because a month name and a clock are a locale and a timezone and the browser has
both.

**Two things worth keeping from doing it.** The rows all said `airport` — the destination this demo
always orders — so the list was three identical lines; and the first attempt joined the two ends with
`→`, which `GlyphCoverageTest` refused because the bundled face does not have it. An em dash does the
same job and is in the face.

**The receipt was blocked and is not any more.** `FareBreakdown` was declared in `:shared-ui`, which
carries Compose, so no server could build one; two attempts to move the components to `:protocol`
failed and were reverted. The third succeeded and the second failure turned out to be stale imports
rather than a limit of the toolkit — [B-65](B-65-a-server-cannot-build-a-fare-breakdown.md) carries
the correction. The components now live in `:protocol`, the server can compose one, and what is left
here is the screen: a route that answers a ride's fare as a kompot tree and a receipt that draws it.
