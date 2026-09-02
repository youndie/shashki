---
id: screen-rider-trip
title: Trip in progress
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/trip/{rideId}"
parent_feature: feature-the-trip
calls_api:
  - endpoint-rides
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride
---

# Screen: trip in progress

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/TripViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/TripScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderTripInProgress.kt` |

## 0. Entry point and visibility

- **Entry point:** `/trip/{rideId}` — a real address, so the browser's back button and a pasted link
  both work.
- **Shown when:** a ride has been ordered.

## 1. Screen states

From `TripUiState`, and the three stages are the three the trip has: **arriving**, **arrived**,
**in progress**. Everything before them is the order still being placed, which is the picker's screen.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | `Rides.ById` | [endpoint-rides](../api/endpoint-rides.md) |
| `GET /api/rides/{id}/driver` | `Rides.Driver` | [endpoint-rides](../api/endpoint-rides.md) |
| `POST /api/rides/{id}/cancel` | `Rides.Cancel` | [endpoint-rides](../api/endpoint-rides.md) |

## 3. Initialisation

**Input parameters:** `rideId` from the route.

**Two loops, and they are two on purpose:** the ride's status changes a handful of times in twenty
minutes; the car moves every few seconds and its position is the thing that goes missing in a tunnel.
One loop asking for both would make a silent phone look like a lost ride.

| Call | Condition | Result |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | every 2 s | the stage, and the terminal states |
| `GET /api/rides/{id}/driver` | every 2 s | where the car is |

## 4. UI elements, top to bottom

### 4.1. The map with the route and the car

- **From:** `MapScene` — the road in two phases, the car, two pins.
- **Display:** the travelled phase is stroked first so the part still to drive is on top where they
  meet at the car; both are `line-cap: butt`, which is why the two phases are two lists rather than a
  line and a progress fraction.
- **A driver with no position leaves the previous car where it was.** The phone is quiet, the car has
  not vanished.

### 4.2. Destination, distance and time

### 4.3. The driver row

- **Display:** name, car, rating — and the registration as a **blank plate**. See the quirk below.

### 4.4. The action bar

- **On tap:** cancel.

| Case | Handling | Screen state |
| :--- | :--- | :--- |
| `200` | the ride becomes `CANCELLED` and the screen finishes | — |
| `400` | report once | unchanged |

## 5. Navigation (summary)

- the ride ends (completed or cancelled) ──▶ back to [screen-rider-class-picker](screen-rider-class-picker.md)

## 6. Quirks

* **The driver's name, car and plate are drawn from nothing.** `RideView` carries a `driverId` and no
  person; the screen draws em dashes because a number in the wrong place reads as an answer.
* **Cancelling costs a quarter of the fare once a driver is assigned**, and the screen does not say
  so. That is a real gap: the rider is not told the price of the button they are pressing.
