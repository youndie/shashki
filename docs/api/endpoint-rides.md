---
id: endpoint-rides
title: Rides — asking for a car, reading one, cancelling it
type: api_endpoints
status: active
services:
  - shashki-server
contract_source:
  - shashki:protocol Rides
  - shashki:protocol RideRequest, RideView, AssignedDriverView, RideHistoryView
parent_feature: feature-order-a-ride
---

# API: rides

> The complete route reference for the rider's own surface. Payload shapes live in the contract
> classes named in `contract_source`; they are compiled into both sides, so there is nothing here to
> copy and nothing to keep in step.

## Routes — all of them, no exceptions

| Method and path | Auth tier | Purpose |
|---|---|---|
| `POST /api/rides` | rider token, when a provider is configured | asks for a car; starts the order saga |
| `GET /api/rides/{id}` | rider token, when a provider is configured | the ride as the rider sees it |
| `POST /api/rides/{id}/cancel` | rider token, when a provider is configured | **two mechanisms under one word** — see below |
| `GET /api/rides/{id}/driver` | rider token, when a provider is configured | where the assigned car is, or a driver with no position |
| `GET /api/rides/{id}/history` | public | what happened to the ride, read from the broker's topic |

**All four are inside `riderRoutes()` and this table said otherwise on its first draft** — it had the
two reads as public, which is what a reader would guess and not what `RideRouting.kt` does. Checking
the tiers against the routing file rather than against memory is the reason this column exists, and
it found its own document wrong before it found anything else.

**"When a provider is configured" is the whole tier.** `configureAuth` is installed only when
`SHASHKI_OIDC_ISSUER` is set, so a demo pointed at nothing runs with these routes open — which is a
switch that is off by default, and therefore one nobody notices is off. Both sides are tested:
`ProtectedRidesTest` requires a 401 with no token and success with one, and points the validator at an
address nothing answers on so the refusal cannot be happening after a network call.

**The rider application satisfies this tier since [B-41](../backlog/B-41-the-rider-actually-signs-in.md).**
For thirty-nine items it did not: the token was obtainable and nothing attached it.

**What is *not* checked by any tier: ownership.** `riderId` is a field of the request body until B-09
puts it in the token, so a token proves somebody signed in and not that the ride is theirs.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST /api/rides`, `GET /api/rides/{id}`, cancel, driver | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/RideRouting.kt` |
| `GET /api/rides/{id}/history` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/events/EventsRouting.kt` |

## Request and response bodies

`protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Ride.kt` — `RideRequest`,
`RideView`, `RideHistoryView`. Do not copy the fields; the classes are the contract.

## Cancelling is two different things

| When | What happens | What the rider is charged |
|---|---|---|
| the saga is still waiting for a driver | the order saga compensates from the middle: the offer withdrawn, the driver freed, the hold released | nothing |
| a driver is assigned and the trip has not started | a **settlement with a fee** — the same five phases as a fare, a quarter of it | the fee |
| the ride is `IN_PROGRESS` or over | refused | — |

That divergence is [research §1.4c](../research/research-architecture.md) and is most of why this
product exists; `SettlementTest` shows both numbers in one test.

## The two things a rider does when it is over (B-44)

| Method and path | Auth tier | Purpose |
|---|---|---|
| `POST /api/rides/{id}/rating` | rider token | one to five, once, and only after `COMPLETED` (204) |
| `POST /api/rides/{id}/tip` | rider token | money on top: a **charge**, not a bigger capture |

**A tip is a settlement of its own.** The hold the order saga took was the quote and the fare's
capture consumed it; `capture` cannot exceed a hold here or at a real provider, so a tip is a fresh
authorisation and capture against the card — `PaymentGateway.charge` — with its own payout row
(`payouts.kind = TIP`), its own saga id (`<ride>:tip`), its own outbox event (`<ride>:tipped`) and
its own compensation, which refunds *that* charge and not the fare. The driver keeps all of it: a
platform cut of a tip is a policy, and a demo that invented one would be teaching it.

**Both are refused with 409 before the ride is over**, which is the same answer for the same reason
as the settlement's own: the request is well formed and will be correct in a few minutes.

**`RideView.chargedCents` is what was actually taken**, which is what R8 shows. The quote is what the
ride was going to cost; for a cancellation the two differ by three quarters.

**`RideView.cancellationFeeCents` is that table as a number** (B-43): `0` while the saga is waiting,
the fee once a driver is assigned, `null` when the ride can no longer be cancelled. It is on the wire
because R10 shows the amount before the button, and a client that multiplied the fare by 0.25 itself
would be a second copy of a pricing rule — the first coefficient change would have the screen
promising one number and the settlement charging another.

## Errors

| Condition | Status | Body |
|---|---|---|
| no such ride | `404` | `{"error": "ride <id> not found"}` |
| cancelling a ride that is `IN_PROGRESS` or over | `400` | `{"error": "ride <id> is <status> and cannot be cancelled"}` |
| both ends outside the service area | `422` | `{"error": "..."}` — the request is well formed and there is no road |
| no token, with a provider configured | `401` | — |
| two writers on one saga row | `409` | `{"error": "concurrent modification, retry"}` |

A ride the projection has never heard of answers `GET .../history` with an **empty list**, not 404:
"the broker has nothing about it" and "there is no such ride" are different facts, and the ride's own
route already answers the second.
