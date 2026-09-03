---
id: B-61
title: "R9's rows carry one address and no date, and R9·b does not exist"
status: done
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

## What it turned out to be: the screen that made the mechanism load-bearing (2026-09-03)

`GET /api/rides/{id}/receipt` answers a kompot tree; a row whose ride is over opens `/receipt/{id}`;
the card is drawn by the renderer that had had no caller since kompot was wired up. **Every number on
it was moved by a settlement** — the figure is the fare charge plus the tip charge, read from the two
sagas that made them, and `SettlementTest` compares it with what the payment gateway recorded rather
than with what the tree says about itself.

**The receipt is read out of the settlements, not recomputed from the pricing rules.** Re-deriving
`base + per km + per minute` in the receipt would be a second application of a formula whose constants
can change: an old ride would show today's lines under yesterday's total, and the two would be off by
an amount nobody could explain. A cancelled ride therefore shows both numbers — the fare it would have
been and the fee that was taken — and does **not** name the percentage, which is `Commission`'s.

**Three things this turned up that the item did not ask for.**

* **A screen the server sends could be invisible, and one was.** kompot falls back to
  `MaterialTheme.colorScheme.onSurface` for a text with no colour of its own — right for a toolkit,
  `#1D1B20` on black here. The promo screen's title had been drawn at **1.23:1 since B-32** with a
  golden photographing it every run. Found by measuring the new PNG rather than by looking at it, and
  fixed at the boundary: `ShashkiDesignSystem` fills in the kit's ink, `DesignSystemInkTest` holds
  every token to it in both themes and fails without the line. D11's table is amended — "cannot name
  a colour" was checked, "need not name one" was not.
* **The money formatter moved to `:protocol`.** `money`, `asDistance` and `asDuration` were in
  `:shared-ui`, and the server needs the same strings — its own copy would be the second way of
  writing a price that the file's own header exists to prevent. They are pure arithmetic over the
  wire types, so they belong beside them.
* **D11 said one server-driven screen and now there are two.** The amendment is in the research at
  the point of divergence: R9·b is not in the flow, it meets D11's own "nothing depends on it" test
  better than the promo does, and the promo alone was never exercising this product's own components.

**Where a row goes is the view model's decision.** `HistoryUiState.settled` is the set of finished
rides; a ride still running opens the trip screen. A flag on the row itself would have been a routing
decision inside a `TripRow`, which is a wire component a server may also send.
