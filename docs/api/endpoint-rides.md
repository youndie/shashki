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

## Why a ride was cancelled (B-58)

`RideView.cancellationReason` carries the sentence the saga refused with — `no cars nearby` for a
cascade that ran out — and the rider's R5·a shows it rather than assuming it.

**It was `null` for every ride until B-58**, and not by an oversight in one place: petich's `Reject`
and `Compensate` take a reason and keep it nowhere, and only the results that are *not* refusals
carry an enriched payload. The reason is written from outside the engine, where `process` returns.

## Outside the city (B-57)

`POST /api/rides` answers **422** with `the pickup is outside the area this service covers` — the
same status `/api/routes` and `/api/quotes` give for the same condition, and no ride row is written.

**It used to answer 500** with GraphHopper's own sentence about a coordinate and a bounding box.
petich runs `ENRICHMENT` before `VALIDATION`, so `QuoteStep` asked the router before `ServiceAreaStep`
— the step whose whole purpose is to refuse this politely — ever ran, and the router's throw arrived
as a systemic saga failure. The check now happens in `RequestRideUseCase`, before a saga exists; the
step stays, because a saga resumed from a row has to validate again.

**And the area is the graph's own bounds.** It used to be a constant beside the step (45.95…46.30 /
14.35…14.70) while the extract spanned 45.867…46.264 / 14.221…14.827, so a point could be inside one
and outside the other in both directions. `RouteEstimator.servedArea` is now the one answer, read
from the loaded graph.

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

## `GET /api/rides?mine=true` (B-45)

The rider's own rides, newest first. **A parameter on the collection rather than a route of its own**,
because "which rides" is a filter and `/api/rides/mine` would be a path segment competing with an id.
Without `mine` the request is refused with 400: this server has no "everybody's rides" and will not
invent one.

**Whose a ride is comes from the token's address** — the one thing about a rider the order saga did
not take from a request body, put there by B-26 so a receipt could not be sent to somebody else's
inbox. With no provider configured there is no address on any row and no principal on any request,
and every ride belongs to the one rider the demo has; that is the honest reading of it rather than an
empty list nobody can explain.

**Newest first needs a timestamp, and petich's table has none** — `id, type, phase, index, status,
payload, enriched, version, suspended_until`. Rather than adding a column to a schema petich owns,
`OrderPayload` carries `requestedAtEpochMs`; rows written before it sort last, which is what a `0`
honestly means.

## The two things a rider does when it is over (B-44)

| Method and path | Auth tier | Purpose |
|---|---|---|
| `POST /api/rides/{id}/rating` | rider token | one to five, once, and only after `COMPLETED` (204) |
| `POST /api/rides/{id}/tip` | rider token | money on top: a **charge**, not a bigger capture |
| `GET /api/rides/{id}/receipt` | rider token | R9·b as a **kompot tree** — the card the server composed (B-61) |

**The receipt is a screen and not a DTO, and that is the decision** ([D11](../research/research-architecture.md)'s
amendment). What a receipt says — which lines, in which order, which of them is the figure — follows
from what the settlements charged, and both are the server's. A `Receipt` DTO would put the layout
decision in every client and let two of them disagree about a card charge; a tree puts it where the
money is. **404 for a ride nobody has settled**: a ride still running has no receipt, and an empty
card would be a receipt for nothing.

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
