---
id: endpoint-quotes
title: Quotes and routes — what a journey costs before anybody orders it
type: api_endpoints
status: active
services:
  - shashki-server
contract_source:
  - shashki:protocol Quotes, Routes
  - shashki:protocol RouteRequest, QuotesView, ClassQuote, RouteView
parent_feature: feature-order-a-ride
---

# API: quotes and routes

## Routes — all of them, no exceptions

| Method and path | Auth tier | Purpose |
|---|---|---|
| `POST /api/quotes` | **public, and chosen** | one road, priced for every class, with the wait for each |
| `POST /api/routes` | **public, and chosen** | the road between two points: distance, duration, geometry |

Public because a price and a road are facts about the city rather than about a person. R4 draws three
prices on a screen the application reaches while still anonymous, and nothing in either answer names
anybody.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST /api/quotes` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/quote/QuoteRouting.kt` |
| `POST /api/routes` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/route/RouteRouting.kt` |

## One road, three prices, three waits

`POST /api/quotes` estimates the road **once** — the class changes the coefficient, not the road — and
then asks `PickupEta` per class. The wait is the nearest candidate of that class, routed to the
pickup: `null` when there is no candidate, and `null` when there is one the router cannot reach.

`null` is the kit's "no cars nearby" and it is an answer. What it must never be is a number: the wait
is the most-looked-at figure on that screen and a constant there is a decoration.

## The car beside the wait (B-72)

`ClassQuote.car` is the driver record's own string — `Skoda Octavia · white` — for the **nearest**
candidate of the class, the same driver the cascade would offer first. `null` when there is no wait,
and `null` for a candidate this server has no record of: the tile then shows the wait alone rather
than a model guessed from the class. The kit's tile reads `4 min · Kia Rio`; this is the second half.

## Request and response bodies

`protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Route.kt`.

## Errors

| Condition | Status | Body |
|---|---|---|
| no road between the two points | `422` | `{"error": "<what GraphHopper said about which point>"}` |

**A point outside the graph's bounding box and a point far from any road both come back as "no
route", and they mean opposite things** — the first is a request for a city this server does not have.
Research §1.6e records it because a test whose points strayed outside the fixture's box got straight
lines from a fallback and would have passed while proving nothing.
