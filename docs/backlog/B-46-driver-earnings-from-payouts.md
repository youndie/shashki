---
id: B-46
title: "Driver earnings: today, this week, and the payouts that already exist"
status: open
priority: P1
size: S
stage: stage-5-the-rest-of-the-kit
---

# B-46 — Driver earnings: today, this week, and the payouts that already exist

The kit's D6 is a pivot — *today · week · history* — of `EarningsTile`s, and it is the one driver
artboard whose data the server already has: the settlement saga writes a payout row for every
completed ride and for every cancellation fee (B-37). `EarningsTile` exists with a golden (B-17).
The driver bundle's shift screen shows the count of positions sent and, after an accepted ride,
what the server says about it; it never shows a number with a currency sign on it.

- **Sum the payouts, do not recompute the fares.** The tile shows what was *paid*, from the payout
  rows, because that is the number the driver cares about and the one that survives a refund. A
  figure recomputed from fares would agree with the payouts until the first tip refund, and then it
  would be the driver's word against the bank's.
- **The tiles are the kit's grid, with the kit's rule** — four columns, gap 12, a `54` figure only
  for the primary number, `32` for the rest (section 08, rule 3). `EarningsTile` is already
  registered as a kompot component; here it is drawn natively, as B-45 draws `TripRow`, and for the
  same reason.
- The rejected alternative is a server-driven earnings screen. D11's test — "is there a native
  version?" — says yes, and the promo remains the one screen with none.
- Deliberately **not** covered: a payout *to* anybody. The row is the record of what is owed; moving
  money to a driver is a payment provider's product.

- AC: `GET /api/driver/earnings` under the driver token ([B-52](B-52-driver-routes-behind-the-token.md)),
  answering today, this week and all time from payout rows alone.
- AC: `driver_earnings` golden against D6, and its `54` figure is today's sum with `tnum` digits.
- AC: a cancellation fee's driver share appears in the day it was captured, and a refunded tip
  disappears from it — the second is the test that the sum is of payouts and not of fares.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ServerDrivenComponents.kt`
