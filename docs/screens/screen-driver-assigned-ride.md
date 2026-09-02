---
id: screen-driver-assigned-ride
title: Assigned ride
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/trip/{rideId}"
parent_feature: feature-the-trip
calls_api:
  - endpoint-driver
  - endpoint-rides
source: driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip
---

# Screen: assigned ride

## 0a. Code anchors

| What | File |
|---|---|
| View model | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripViewModel.kt` |
| Screen / Content | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverAssignedRide.kt` |

## 0. Entry point and visibility

- **Entry point:** `/trip/{rideId}` inside the driver bundle, reached by accepting an offer.

## 1. Screen states

From `DriverTripUiState`: the ride, and whether a transition is in flight. The **one action** is
derived from the ride's status through `TripProgression`, and is `null` once the ride is over.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | `Rides.ById` | [endpoint-rides](../api/endpoint-rides.md) |
| `POST /api/driver/rides/{id}/advance` | `TripAdvance` | [endpoint-driver](../api/endpoint-driver.md) |

## 3. Initialisation

**Input parameters:** `rideId` from the route.

| Call | Condition | Result |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | every 3 s | the status, and the end of the ride |

## 4. UI elements, top to bottom

### 4.1. Status, fare, and the two addresses

- **Display:** the addresses are coordinates to four decimals, because nothing in this product
  geocodes and a street name invented here would be the client asserting something nobody measured.

### 4.2. The one action

- **Display:** `on my way`, `I am here`, `start the trip`, `finish` — the words are the driver's and
  the states are the server's.
- **On tap:** advances.

| Case | Handling | Screen state |
| :--- | :--- | :--- |
| `200` | **take the state from the answer**, never from the intention | the next state |
| `409` | say what the server said | unchanged |

## 5. Navigation (summary)

- the ride ends ──▶ back to [screen-driver-shift](screen-driver-shift.md)

## 6. Quirks

* **There is no map here.** Turn-by-turn is out of scope for the reference and a map that only showed
  two pins would be a smaller version of the rider's for no reason.
* **The last press is the one that takes the rider's money.** That is why the screen never advances
  optimistically: a hiccup would otherwise show a finished ride and the driver would stop driving.
* **This screen had no buttons at all until the server had the routes** — B-29 shipped it that way and
  said so, rather than drawing a control that posted to nothing.
