---
id: screen-driver-earnings
title: Earnings
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/driver/earnings"
parent_feature: feature-settlement
calls_api:
  - endpoint-driver
source: driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/earnings
---

# Screen: driver earnings

## 0a. Code anchors

| What | File |
|---|---|
| View model | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/earnings/ui/EarningsViewModel.kt` |
| Screen / Content | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/earnings/ui/EarningsScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverEarnings.kt` |
| Golden | `screens_driver_earnings` |

## 0. Entry point and visibility

- **Entry point:** the shift screen's header — where a driver is between rides, which is when they
  look. A second control by the shift switch would crowd the one thing that matters while a shift is
  running.
- **Three pivot items:** *today*, *week*, *history*.

## 1. The numbers are sums of payout rows

**Not of fares.** The payout row is what the settlement wrote down as owed — for a completed ride,
for a cancellation fee, and for a tip — and it is the number that survives a refund. A figure
recomputed from journeys agrees with it until the first rolled-back tip, and then it is the driver's
word against the bank's.

`GET /api/driver/earnings` answers today, this week and all time. **The day and the week are UTC**,
which is a seam rather than a decision: a driver in another timezone sees their day roll at the wrong
hour, and fixing it needs the driver record this product does not have — the same missing thing that
made the class and the rating on a position frame self-reported until B-63 gave this product a
`drivers` row. The timezone is still missing from it, which is why this paragraph is still here.

## 2. One `54` and the rest at `32`

The kit's section 08 rule 3. Today's sum is the page's own figure; the tiles below carry `32`s
through kompot's `EarningsTileRenderer`, drawn natively for the same reason R9 draws `TripRow` that
way — a screen with an obvious native version does not need a server to describe it, and reusing the
renderer is what stops the two drifting.

Both figures are `tnum`: a number that changes while a driver watches it must not shuffle its
neighbours.

## 3. What it does not do

- **No per-ride list yet.** The payout rows carry a ride id and joining them to the rides is exactly
  the list [B-45](../backlog/B-45-history-from-the-broker.md) built for the rider; doing it for the
  driver is that item's shape a second time, and the screen says so rather than drawing an empty grid.
- **No payout *to* anybody.** The row is the record of what is owed; moving money to a driver is a
  payment provider's product.

## The counts and the days (B-81)

Each tile's label carries how many fares its sum is made of — `today · 3 trips` — and the *history*
item lists the payouts **by day**: `3 september · 2 trips` against `$ 46.32`, from `EarningsView.days`,
which the server groups by its UTC day and this client names on its own calendar. Fares are counted
and tips are not: a tip is money in the sum and not a ride in the count. The kit's `avg 396 ₽` is
not drawn — an average of three rides on a stand is a decoration, and the count beside the sum is
the same information without the pretence.
