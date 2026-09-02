---
id: B-46
title: "Driver earnings: today, this week, and the payouts that already exist"
status: done
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

## What it turned out to be

**The item's own sentence was the whole design and it held.** Sum the payout rows, do not recompute
the fares: `PayoutRepository.sumFor(driverId, since)` is three queries and the route is a dozen
lines, because the settlement had already written down everything a driver is owed — the fare's
share, the fee's share when a rider cancelled after a driver set off, and since
[B-44](B-44-finished-rate-and-tip.md) the tip.

**What the item did not say, and what the code now does, is the timezone.** The day and the week are
UTC. That is a seam and it is named where it lives: a driver in another timezone sees their day roll
at the wrong hour, and fixing it needs a driver record — the same missing thing that leaves the class
and the rating on a position frame self-reported.

**The route needed the same escape every other driver route carries.** `GET /api/driver/earnings`
has no path segment and no body to put an id in, so with no provider configured it could not say
whose money it was answering about — the first version threw `no driver identity on this request`
against the demo. `DriverEarnings(driverId)` is that seam, ignored the moment there is a token, and
it is the fourth time B-52's rule has been written out rather than the first time it was needed.

- AC 1: the route, behind the driver's token, answering the three periods from payout rows alone.
- AC 2: `screens_driver_earnings` — one `54` (today's sum, `tnum`) and `32`s in the tiles, drawn by
  kompot's `EarningsTileRenderer` natively.
- AC 3: `earnings are the sum of the payout rows, fare and fee and tip alike` walks a completed ride,
  a tip and a cancellation fee through one day; `a tip that was rolled back is not in the earnings`
  is the half that says it is a sum of payouts rather than of fares.
- Deliberately still open: the per-ride list on the *history* page. The payout rows carry a ride id
  and joining them to the rides is B-45's shape a second time; the screen says so rather than
  drawing an empty grid.
