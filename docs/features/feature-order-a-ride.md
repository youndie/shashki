---
id: feature-order-a-ride
title: Order a ride
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries:
  - screen-rider-class-picker
api:
  - endpoint-rides
  - endpoint-quotes
tags: [saga, compensation]
---

# Order a ride

## 1. Overview

A rider names two points, sees what each class costs and how long the wait is, and asks for a car. The
server prices the journey on real roads, holds the fare on the card, and asks the nearest driver —
one at a time, fifteen seconds each — until somebody takes it or ninety seconds pass.

**This is the feature the product exists to demonstrate**, and the thing being demonstrated is not
that it works. It is that when the process dies between any two of those steps, the card is not left
holding money and no driver is left reserved for a ride nobody is driving.

## 2. Business rules

* Both ends must be inside the service area; a request outside it is refused before anything has a
  side effect.
* One road estimate prices every class — the class changes the coefficient, not the road.
* A hold is taken for the quoted amount before any driver is asked, and released by compensation if
  the ride does not reach a driver.
* Drivers are asked **one at a time**, nearest first and better rated among equals, each for fifteen
  seconds. Not answering is not a decision: the server times it out and moves on.
* Ninety seconds of asking with nobody accepting is "no cars nearby" — the hold released, the rider
  told.
* A rider who cancels while a driver is being asked is charged nothing.
* A class with no candidate cannot be ordered; the tile says "no cars nearby" and the screen refuses
  to select it.

## 3. Flow

1. The rider's client asks `POST /api/quotes` — one road, three prices, three waits.
2. `POST /api/rides` starts the order saga: **ENRICHMENT** prices the road, **VALIDATION** checks the
   service area, **AUTHORIZATION** holds the fare, **EXECUTION** reserves the nearest driver, posts the
   offer and **suspends**.
3. The driver's client polls `GET /api/driver/offers/{driverId}` and answers.
4. An accept resumes the saga: the driver becomes final, **POST_PROCESSING** writes `ride.assigned`
   into the outbox in the same transaction, and the ride is `ASSIGNED`.
5. A decline or a timeout releases that driver, asks the next and resuspends — the cascade.

Every cross-service call in the flow is the rider's or the driver's own credential; nothing here
speaks to another service on its own behalf.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSteps.kt` — one class per phase |
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/` — the geo-index, the candidate query, the offer board |
| shashki-server | `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Ride.kt` — the contract |

## 5. Scenarios

### Scenario: a car is found

* **Given:** a driver of the requested class is online near the pickup
* **When:** the rider asks for a car
* **Then:** the ride is `MATCHING` with the fare held, the driver sees the offer, and accepting makes
  it `ASSIGNED` with one `ride.assigned` event in the outbox
* **Automated:** `shashki RideRoutesTest`

### Scenario: the process dies between any two phases

* **Given:** a ride part-way through the saga
* **When:** the next step never returns — which is what an unplugged process looks like
* **Then:** the saga compensates: **no hold survives, no driver stays reserved, and no event escapes**
* **Automated:** `shashki OrderSagaTest`

### Scenario: the first driver ignores the offer

* **Given:** three candidates and a first one who answers nothing
* **When:** fifteen seconds pass
* **Then:** that driver is released, the next is asked, and the saga stays parked at the same step
* **Automated:** `shashki OfferCascadeTest`

### Scenario: nobody takes it

* **Given:** every candidate has declined
* **When:** the cascade runs out
* **Then:** the saga compensates — `CANCELLED`, reason "no cars nearby", the hold released
* **Automated:** `shashki OrderSagaTest`

### Scenario: a driver answers an offer that has moved on

* **Given:** a driver whose tab was asleep while the cascade went past them
* **When:** they accept
* **Then:** the server answers `409` with "offer for ride … is no longer …'s"
* **Automated:** `shashki RideRoutesTest`

## 6. Out of scope

* Scheduling a ride for later, more than one rider, and anything about a driver's earnings other than
  the payout row [feature-settlement](feature-settlement.md) writes.
* Choosing *which* driver by anything but distance and rating. The candidate query is a grid lookup,
  not a marketplace.

## 7. Quirks

* **`MATCHING` and `REQUESTED` are both "the saga is running"**, and which one a reader sees depends
  on the phase. `PetichRideRepository.rideStatus` is the whole mapping.
* **A completed order saga is an `ASSIGNED` ride, not a completed one.** The first version of
  `RideStatus`'s KDoc said the enum was "the order of the saga" and it is not — research §1.4c.
* **The offer board and the geo-index are lost on restart**, and that costs at most one offer's worth
  of seconds: the saga is still suspended and the sweeper still runs.
* **The wait shown on a class tile is the nearest candidate by straight line, then routed.** Routing
  every candidate to find the quickest would be one graph search per online driver per class, for a
  number shown before anybody has ordered anything.
