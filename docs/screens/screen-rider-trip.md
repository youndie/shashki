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

### 4.2. The figure and its meta

- **While the car is on its way:** the minutes to it at 32 **in the accent** — `3 min` — with
  `2.1 km to you` beside it, from `RideView.leg`, the server's road from the driver's last position
  (B-75, B-76). When the server has no road yet the words stand in: *on its way*.
- **Once it has arrived:** *waiting for you*, in the foreground; the accent goes back to being
  nobody's.
- **During the trip:** the minutes left at 32, `arriving 20:06 · 11.2 km left` beside them, and the
  fare once, under (B-77). The minutes and the kilometres are the server's leg to the drop-off,
  routed from the car's last position; the clock is the view model's, from the rider's own watch —
  the one they will compare it with. Without a leg the screen falls back to the quote.
- **The road behind the car goes to 25 % white and the road ahead stays the accent** — "progress is
  colour, not thickness". The split is at the road vertex nearest the car, made on every position
  the socket reports, and only once the trip is running: before pickup the car is not on this road.

### 4.3. The driver row and the plate

- **Display:** name, car, rating — from the driver record (B-63); dashes for a driver the server has
  no record of.
- **The plate is set as a plate: inverted, SemiBold, 0.06 em tracking** — the kit's "only inverted
  element on a rider screen" (B-76). It used to be the accent surface, which spent the screen's one
  accent on the wrong thing; the kit gives the accent to the minutes above and inverts the plate so
  it is found first anyway.

### 4.4. The action bar

- **On tap:** cancel.

| Case | Handling | Screen state |
| :--- | :--- | :--- |
| `200` | the ride becomes `CANCELLED` and the screen finishes | — |
| `400` | report once | unchanged |

### 4.5. R7·a — gps lost (B-80)

When the driver's socket has been silent for half a minute, a **full-width band at the join of the
map and the panel, in chrome, never a floating card**: `gps lost · last position 40 seconds ago. The
trip is running and the fare is held at the last confirmed point.` The map dims — the page's ground
at 60 % over it, which is what a canvas drawing its own tiles can do for "desaturates to 40 %". The
car stays where it was; the band says how long; a position takes it down. Ten seconds in a tunnel
is not a lost car, which is why the band waits.

## 5. Navigation (summary)

- the ride ends (completed or cancelled) ──▶ back to [screen-rider-class-picker](screen-rider-class-picker.md)

## 6. Quirks

* ~~The driver's name, car and plate are drawn from nothing.~~ **Answered by B-63**: the server has a
  driver record and the screen draws it; the dashes remain only for a driver it has no record of.
* ~~Cancelling costs a quarter of the fare and the screen does not say so.~~ **Answered by B-43**:
  R10's message box carries the amount before the button.
* **No photo and no trips count.** The kit's DriverCard has both; the wire has neither, and a photo
  invented for a screen is the fabrication B-47 refused elsewhere (B-76).
