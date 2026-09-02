---
id: screen-rider-matching
title: Looking for a car
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/matching/{rideId}"
parent_feature: feature-order-a-ride
calls_api:
  - endpoint-rides
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride
---

# Screen: looking for a car

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/MatchingViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/MatchingScreen.kt` |
| The confirmation's copy | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/CancelCopy.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderMatching.kt` |
| Goldens | `screens_rider_matching`, `screens_rider_no_cars_nearby`, `screens_rider_cancel_confirm` |

## 0. Entry point and visibility

- **Entry point:** `/matching/{rideId}` — an address of its own, so a reload while a rider waits comes
  back to the wait rather than to an empty picker.
- **Shown when:** a ride has been ordered and no driver has taken it yet.
- **Left when:** a driver accepts (the trip replaces this screen in the stack, so *back* from a trip
  does not offer to watch the search again), or the rider goes back to the picker.

## 1. Screen states

Three artboards, **one screen**, because they share the ride and differ only in what the server said.

| State | Artboard | What the server said |
|---|---|---|
| looking | R5 | `MATCHING` — the saga is asking drivers |
| no cars nearby | R5·a | `CANCELLED`, and this screen did not ask for it |
| cancel | R10 | over either of the two, on the rider's own tap |

**`CANCELLED` is one status for two events**, and only the client can tell them apart: the cascade
running out of drivers, and this rider pressing cancel a moment ago. The view model knows because it
is the one that pressed — `MatchingUiState.cancelling` is set before the call and never lowered.
Getting this wrong shows "no cars nearby" to somebody who cancelled, which is a screen blaming the
city for the rider's own decision.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | `Rides.ById` | [endpoint-rides](../api/endpoint-rides.md) |
| `POST /api/rides/{id}/cancel` | `Rides.Cancel` | [endpoint-rides](../api/endpoint-rides.md) |

Every two seconds, through `ObserveRideUseCase` — the same loop the trip screen uses, which stops
itself at a terminal status.

## 3. UI elements, top to bottom

### 3.1. The headline

24/300 while the search runs, **54/200** once it has failed. That difference is the whole hierarchy
of the pair: one is a status, the other is an answer.

### 3.2. The dots

The kit's five, a 4.4-second cycle, and **only while the search is running**. Leaving them under "no
cars nearby" would say the search continues, which is the one thing that screen exists to deny.

### 3.3. The destination row

Where the ride is going and what the journey is, from the quote the order already produced.

### 3.4. One action, in the bar

*cancel* while looking, *try again* when the cars have run out. **There is no map**: the rider is
waiting, not choosing, and the kit answers that with a state screen rather than a spinner over the
previous one.

**"notify me" is not here.** The artboard has it beside *try again*; it needs a subscription and a
push, and this product has neither. A disabled button is a promise, so the button is absent.

### 3.5. R10, over the top

The kit's message box, with the amount in it. **The fee is shown before the button** — a
confirmation that says "a fee may apply" is a product hiding its own rule, and this one exists to
show the seam. The number comes from `RideView.cancellationFeeCents`, which the server computes from
the same `Commission` the settlement charges: `0` before a driver has set off, a quarter of the fare
after, `null` once the rider is in the car and there is nothing to confirm.

The same confirmation is on the trip screen, because that is where cancelling actually costs money.

## 4. What it does not do

- No *notify me*, as above.
- No estimate of how long the search will take. The server does not know: the cascade's budget is
  ninety seconds, and a countdown to it would promise a car at the end of it.
