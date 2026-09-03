---
id: screen-rider-class-picker
title: Class picker
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/"
parent_feature: feature-order-a-ride
calls_api:
  - endpoint-quotes
  - endpoint-rides
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride
---

# Screen: class picker

## It asks again while it is open (B-66)

Every five seconds, and only while the screen is on top: the loop is a `suspend fun` the composable
runs in a `LaunchedEffect`, so a picker underneath a trip is asking nobody anything.

**It used to ask once.** A rider who opened the application before anybody was driving read
*no cars nearby* until they restarted it — the price does not go stale and the wait does, and the two
arrive in one answer, which is what made caching both together easy to do by accident.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/ClassPickerViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/ClassPickerScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderClassPicker.kt` |
| Wiring | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/RiderModule.kt` |

## 0. Entry point and visibility

- **Entry point:** the application's start route, `/`.
- **Shown when:** always. Signing in is not a precondition — prices are public.

## 1. Screen states

From `ClassPickerUiState`:

- **Loading** (`loading = true`): every tile unavailable, the meta an ellipsis. **This is the state a
  rider actually meets first and the one nobody draws on purpose** — a golden of it exists for that
  reason.
- **Content**: three tiles, one selected, the order bar carrying the selected fare.
- **A class with no cars**: the kit's unavailable tile, "no cars nearby", not selectable.
- **Error**: the load failed; the screen says so rather than hanging on "…".

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `POST /api/quotes` | `Quotes`, `QuotesView` | [endpoint-quotes](../api/endpoint-quotes.md) |
| `POST /api/rides` | `Rides`, `RideRequest` | [endpoint-rides](../api/endpoint-rides.md) |

## 3. Initialisation

**Input parameters:** none — the pickup and the dropoff come from `RiderConfig` until there is an
address search.

**Requests on load:**

| Call | Condition | Result |
| :--- | :--- | :--- |
| `POST /api/quotes` | always | three prices and three waits |

**Handling the responses:**

| Call | Case | Handling | Screen state |
| :--- | :--- | :--- | :--- |
| quotes | `200` | map to tiles; **move the selection to a class that has cars** | Content |
| quotes | failure | report once | Error |

## 4. UI elements, top to bottom

### 4.1. The map

- **From:** `MapScene` with the ride's camera; the basemap is [feature-the-map](../features/feature-the-map.md).
- **Display:** 360 dp of the 844 dp canvas — a *sized element*, which is the whole of D1's argument.

### 4.2. Destination and its meta

- **Field:** `uiState.destination`, `distanceMetres`, `durationSeconds`
- **Display:** `22.8 km · 35 min`, or `…` while loading.

### 4.3. The three class tiles

- **Field:** `uiState.quotes`
- **Display:** name, the wait (`4 min`) or `no cars nearby`, and the fare (`$ 24.90`). The formatting
  is the application's and is what `:rider`'s own goldens exist to check.
- **On tap:** selects — **unless the class has no cars**, in which case the tap is ignored. The kit's
  tile draws the unavailable state and still reports a click, so refusing is this screen's job.

### 4.4. Payment row

- **Display:** `card ·· 4417`, a constant. "change" does nothing: there is no payment method store.

### 4.5. The order bar

- **Display:** `order · $ 24.90`
- **On tap:** `POST /api/rides` with the selected class.

| Case | Handling | Screen state |
| :--- | :--- | :--- |
| `201` | navigate to the trip | — |
| failure | report once | unchanged |

## 5. Navigation (summary)

- order accepted ──▶ [screen-rider-trip](screen-rider-trip.md)

## 6. Quirks

* **The wait and the car are `4 min · Kia Rio` in the kit and only the first is real.** The vehicle is
  a dash, because `RideView` carries a `driverId` and nothing about the car, and the registration is
  what a rider checks a real car against.
* **This screen loads without a token and orders with one.** Prices are public, so it draws before
  anybody signs in; `POST /api/rides` is not, so the first order redirects to the provider and comes
  back. That asymmetry is the tier table doing its job rather than an inconsistency.
* **The opening selection follows the cars.** `ECONOMY` is the default before anything is known; if
  nobody is driving one, staying there would leave a greyed row with the order bar live under it.
