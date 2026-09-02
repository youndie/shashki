---
id: screen-driver-shift
title: Shift
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/"
parent_feature: feature-the-trip
calls_api:
  - endpoint-driver
source: driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift
---

# Screen: shift

## 0a. Code anchors

| What | File |
|---|---|
| View model | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/ui/ShiftViewModel.kt` |
| Screen / Content | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/ui/ShiftScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverShift.kt` |
| The socket | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/data/WebSocketShiftRepository.kt` |

## 0. Entry point and visibility

- **Entry point:** the driver bundle's start route, served at `/driver` in the image.
- **Shown when:** always.

## 1. Screen states

From `ShiftUiState`. **Three states and one screen, because that is how a shift feels** — the offer
does not arrive on a new page; the driver was looking at this screen and now there is a card.

- **Offline**: a word and a button.
- **Waiting**: "waiting", and the count of positions the socket has taken.
- **An offer**: the kit's `OfferCard` with its draining bar.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `WS /api/driver/positions` | `DriverReport` | [endpoint-driver](../api/endpoint-driver.md) |
| `GET /api/driver/offers/{driverId}` | `DriverOffers.ForDriver` | [endpoint-driver](../api/endpoint-driver.md) |
| `POST /api/driver/offers/{rideId}/answer` | `OfferAnswer` | [endpoint-driver](../api/endpoint-driver.md) |

## 3. Initialisation

**Input parameters:** none; the driver's id, class and rating come from `DriverConfig`.

**Nothing runs on open.** Going online is a tap, and it starts three loops: the position socket, the
offer poll, and — when an offer arrives — the countdown.

## 4. UI elements, top to bottom

### 4.1. The driver label and class

### 4.2. The waiting state

- **Field:** `uiState.reported`
- **Display:** `42 positions sent`. **The count is the point**: "waiting" is a word an application can
  print over a dead socket, and a number that has stopped rising is not.

### 4.3. The offer card

- **Fields:** the fare, the class, the two addresses, `secondsLeft` of `secondsTotal`
- **Display:** fare and remaining seconds at 54, everything else at 17 or below, and the bar draining
  left to right is the only moving thing on the screen — a two-second read.
- **The pickup's meta is a dash**, because the quote the server sends is the rider's journey and a
  number borrowed from the wrong leg reads as an answer.
- **On accept / decline:** answers the offer.

| Case | Handling | Screen state |
| :--- | :--- | :--- |
| accepted | navigate to the assigned ride | — |
| `409` — it went to somebody else | say so; the driver stays online and still a candidate | Waiting |
| failure | report once | the card stays |

### 4.4. The shift switch

- **On tap:** go online or offline. Going offline closes the socket, which is how the server hears
  about it.

## 5. Navigation (summary)

- an offer accepted ──▶ [screen-driver-assigned-ride](screen-driver-assigned-ride.md)

## 6. Quirks

* **The countdown counts a duration the server handed over**, not a difference this client computed:
  a laptop an hour out would otherwise draw fifteen seconds that never start.
* **Reaching zero drops the card and the screen does not put it back**, even though the board may
  still be offering it for one more poll. Without that the card came back two seconds later with a
  fresh fifteen on it.
* **There is no address for an offer.** A link to a thing that lives fifteen seconds is broken by
  design, so the offer is a state of this screen rather than a route.
