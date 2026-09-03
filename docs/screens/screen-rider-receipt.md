---
id: screen-rider-receipt
title: Receipt
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/receipt/{rideId}"
parent_feature: feature-the-trip
calls_api:
  - endpoint-rides
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/receipt
---

# Screen: receipt

The kit's R9·b, and **the second screen in this product the server composes** — the first made of
this product's own components rather than kompot's stock vocabulary.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/receipt/ui/ReceiptViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/receipt/ui/ReceiptScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderReceipt.kt` |
| The tree it draws | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/domain/ReceiptScreenUseCase.kt` |
| Goldens | `screens_rider_receipt`, `screens_rider_receipt_light` |

## 0. Entry point and visibility

- **Entry point:** a row on [R9](screen-rider-history.md) whose ride is over — `COMPLETED` or
  `CANCELLED`. A row whose ride is still running opens the trip screen instead, and which of the two
  it is is decided by the history's view model, because that is the half that read the statuses.
- **Address:** `/receipt/{rideId}`, an address of its own because a card charge is the sort of page
  people bookmark.

## 1. Screen states

- **Loading:** one ellipsis in the subtle brush. Nothing else: a skeleton of a card whose shape the
  server has not sent yet would be this client guessing the layout.
- **Content:** whatever tree came back, drawn by `ServerScreen` — in practice a title, the ride's id
  and one `FareBreakdown`.
- **Empty and error are the same state, deliberately:** "no receipt for this ride yet". A ride that
  has not settled answers 404 and means it; a network failure answers nothing. A screen that claimed
  to tell them apart would be inventing the difference.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides/{id}/receipt` | `Rides.Receipt` | [endpoint-rides](../api/endpoint-rides.md) |

## 3. Initialisation

**Input parameters:**

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `rideId` | `String` | from the route; the view model is resolved with it, as `TripViewModel` is |

The fetch starts in `init`. There is nothing else to load: this screen has no state of its own.

## 4. What is on it, and who decided

### 4.0. When, above the card (B-79)

`2 september · 19:40` at the page margin, in the meta brush — **the one line on this screen the
client writes**, because a date is a calendar and a timezone and the browser has both (B-61). It is
read off the ride's `requestedAtEpochMs`, not off the tree, and the server's card begins under it.

### 4.1. The card

- **Where it comes from:** the server, entirely. `FareBreakdown.amount` is the fare charge plus the
  tip charge, both read from the settlements that made them; the lines are those same numbers named.
- **What this client contributes:** the kit. The figure is 54 because the tree marks the card
  `primary`; every line under it is capped at 19 by the kit's composition rule, which lives in
  `FareBreakdownRenderer` and not on the wire.
- **What this client does not do:** arithmetic. There is no fare in the rider's receipt package, no
  currency and no total — [B-61](../backlog/B-61-the-history-row-and-the-receipt.md) asks for that by
  name, and the way it is enforced is that the state holds a `KompotComponent` and nothing else.

### 4.1a. The journey and the driver (B-79)

Both ends above the card and the driver under it — `Ivan Sokolov · Skoda Octavia · white · A 123
BC`, then `rated 4 of 5` when this rider rated the ride — all of them the server's texts, from the
settlement's own record of the journey, the driver record and the rating. **The ride's identifier
is no longer on the screen**: it had been the second line because the tree needed one and the server
had nothing else to hand, and an id is for a log.

### 4.2. A cancelled ride

Two lines rather than one — the fare it would have been, and the fee that was taken. A receipt
showing only the fee leaves a rider working out a quarter of a number nobody told them. The
percentage is **not** on the wire: it is `Commission`'s, and repeating it would be a pricing rule in
a second place.

### 4.3. The back bar

The kit's 54 dp chrome row, drawn only where the platform has none of its own — the browser has a
back button people already use ([B-67](../backlog/B-67-no-way-back-in-the-window.md)).

## 5. What this screen is for

**It is the screen that made the server-driven mechanism load-bearing.** The promo screen proves a
client can draw a tree it has never seen; this one proves the tree can be about something that
matters, and it exercises the whole path end to end: a component declared in `:protocol`, built by a
server that has no Compose in it, registered by KSP in two halves — the serializers beside the
component, the renderers beside the renderer — and drawn here.

**The degradation sink is this screen's own**, injected under `named("receipt")`, because "a client
could not draw `fare_breakdown`" is a fact and "on the receipt" is what makes it actionable.
