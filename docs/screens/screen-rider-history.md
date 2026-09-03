---
id: screen-rider-history
title: Trips
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/trips"
parent_feature: feature-the-trip
calls_api:
  - endpoint-rides
  - endpoint-screens
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/history
---

# Screen: trips

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/history/ui/HistoryViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/history/ui/HistoryScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderHistory.kt` |
| Goldens | `screens_rider_history`, `screens_rider_history_empty` |

## 0. Entry point and visibility

- **Entry point:** `/trips`, reached from the class picker's overflow dots — which had the kit's
  shape and nothing behind them until this screen existed.
- **Three pivot items:** *trips*, *profile*, *promo*.

## 1. The pivot is the top level

The kit's rule 5, which [B-17](../backlog/B-17-kompot-renderer-invariants.md) turned into a
renderer invariant: nothing may nest a pivot, and here it simply *is* the screen. The third item
hosts the same `PromoScreen` the server owns ([D11](../research/research-architecture.md)) rather
than a copy of it.

## 2. `TripRow` is drawn natively and stays a kompot component

That is the property rather than a contradiction. D11 gives the server one screen because a list of
somebody's own rides has an obvious native version, and the argument for a server-driven screen is a
screen with none. This list calls kompot's own `TripRowRenderer`, so a list this application
assembles and one a server sends cannot drift apart.

**A plain `Column`, not a `LazyColumn`.** The pivot measures its page with an unbounded height and a
lazy container inside one throws. A rider's history here is a handful of rows; when it is not, the
list wants a screen of its own with the scroll that comes with it.

## 3. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides?mine=true` | `Rides(mine)` | [endpoint-rides](../api/endpoint-rides.md) |

**The list is the saga store and the detail is the broker's projection.** Which rides are mine is a
question only the store can answer; what each one went through is
`GET /api/rides/{id}/history`, read per ride. Keeping them apart is the point: this is the one screen
where a reader could see the two stores disagree if they ever did.

## 3a. Where a row goes (B-61)

A row whose ride is over — `COMPLETED` or `CANCELLED` — opens
[R9·b, the receipt](screen-rider-receipt.md); one still running opens the trip screen that follows
it. **The view model decides which**, and not the drawing: `RiderHistory` is handed one `onTrip` and
`HistoryUiState.settled` is the set of ids that are finished. A flag on the row itself would be a
routing decision inside a `TripRow` — a wire component a server may also send.

## 3b. The row is a route stack (B-78)

Both ends of the journey as two lines, each led by its pin, with the amount on the right — the kit's
rule 4, *a row leads with a route stack, one glyph, or nothing*. `TripRow.from` and `to` carry the
two lines; `title` remains for a row with one end or none, and for a consumer that does not draw
stacks. The stack is what keeps every row one height: the em dash it replaced wrapped beside a wide
amount and not beside a narrow one.

**The pivot's third header is cut at the window's edge, and that is the kit's own motion.** Metro's
pivot shows the next item's header peeking past the edge and "wraps round" as the pages turn; the
sweep read it as a clipping defect and it is the library's `KvadrantPivot` doing what the kit says.

## 4. What the rows say about money

The amount on a row is `RideView.chargedCents` — **what was taken**. A finished ride shows the fare, a
ride cancelled after a driver set off shows the quarter that was charged as a fee, and a ride nobody
drove shows `$ 0` — the kit's row says the zero (B-78). `—` is for a sum that does not exist *yet*:
a ride still running. The two cancellations are told apart in the list exactly as they are in the
settlement.

## 5. The empty state

The kit's section 08: one line in the disabled brush, no action. A button would invite the rider to
fix something they have not done wrong. **"Loading" is not an empty list** and says nothing at all
until the answer is in — the two look identical and mean opposite things.

## 6. Profile, and its limit

The name is the configured rider id and the address is the token's `email` claim, read **without
verification**: deciding whether a signature is ours is the server's job, done once, in shildik's
verifier. There is nothing to edit, which is the item's own limit.
