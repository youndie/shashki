---
id: feature-the-trip
title: The trip
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries:
  - screen-driver-shift
  - screen-driver-assigned-ride
  - screen-rider-trip
api:
  - endpoint-driver
tags: []
---

# The trip

## 1. Overview

Between a driver accepting and the money moving there is a stretch that is not a saga: the driver sets
off, arrives, starts the trip and finishes it. Nothing in it needs compensating — the fare is already
held and the driver is already reserved — so it is four states on a row rather than five phases with
rollbacks.

The rider watches the same four states from the other side, with the car moving on the map.

## 2. Business rules

* Only the driver the order saga assigned can move the trip; anybody else gets a 404, because
  confirming that somebody else's ride exists is itself an answer.
* Transitions go one at a time and in order: `ASSIGNED → ARRIVING → ARRIVED → IN_PROGRESS →
  COMPLETED`. A skipped state is refused with the one that was expected.
* Reaching `COMPLETED` starts the settlement. Nothing else in the product captures money.
* A rider may cancel until the trip is `IN_PROGRESS`; after that the fare is the fare.
* The trip's row appears on the driver's **first** transition. A ride with no row has not been
  started, and that is what an absent row means rather than a missing record.

## 3. Flow

1. The driver's client posts `POST /api/driver/rides/{id}/advance` with the state it believes is next.
2. The server checks the driver against the one the order saga recorded, and the transition against
   `TripProgression`.
3. The row is written; on `COMPLETED` the settlement saga is started **after** it, so a settlement
   that throws leaves a finished trip that can be retried rather than an unfinished ride whose money
   has moved.
4. The rider's client polls `GET /api/rides/{id}` and `GET /api/rides/{id}/driver` on two separate
   loops, because a status and a position change at different speeds and go missing separately.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/trip/` |
| shashki-server | `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Ride.kt` — `TripProgression`, `TripAdvance` |
| shashki-server | `server/src/main/resources/db/migration/V2__trips_and_payouts.sql` |

## 5. Scenarios

### Scenario: the driver drives the trip to its end

* **Given:** an assigned ride with the fare held
* **When:** the driver advances through all four states
* **Then:** each step leaves the money alone, and the last one settles the ride: the hold captured, a
  payout recorded, nothing left holding money
* **Automated:** `shashki SettlementTest`

### Scenario: a transition out of order

* **Given:** a ride that is `ASSIGNED`
* **When:** the driver says it is `COMPLETED`
* **Then:** `409`, the ride is unchanged, and no money has moved
* **Automated:** `shashki SettlementTest`

### Scenario: somebody else's ride

* **Given:** a ride assigned to `driver-1`
* **When:** `driver-9` tries to advance it
* **Then:** `404`
* **Automated:** `shashki SettlementTest`

## 6. Out of scope

* **Turn-by-turn navigation.** [B-23](../backlog/B-23-routes-and-eta-on-embedded-graphhopper.md)
  settled that it is out for the reference and nothing since has changed it.
* Anything about the vehicle: `RideView` carries a `driverId` and no model, colour or registration,
  and the screens draw those as dashes because the registration is the field a rider checks a real car
  against.
* Location-driven transitions. The four states are the driver's taps; a real system would move some of
  them from a geofence.

## 7. Quirks

* **The trip overlays the saga's row and only while it says `ASSIGNED`.** A cancelled order saga stays
  cancelled whatever a stale trip row claims — the saga is the record, the trip is the overlay.
* **The driver's client takes its state from the answer, never from the button.** The request that
  moves a trip to `COMPLETED` is the one that captures money, so a screen that advanced optimistically
  would show a finished ride whenever the network hiccuped.
* **The geolocation is real where there is one, and named where there is not** (B-49). The bundle
  watches `navigator.geolocation` and sends what it produces; with no permission, no device or no
  browser API it sends its configured point and the shift screen says `position: configured`. What
  has not changed is the thing B-29 refused: nothing here fabricates a drift, because that is the
  client inventing data the server indexes as fact. The cadence stays the bundle's — a report every
  four seconds, carrying the newest fix — rather than the device's, which in a moving car is several
  a second and in a parked one is none.
