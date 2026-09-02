---
id: screen-rider-finished
title: Finished
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/finished/{rideId}"
parent_feature: feature-the-trip
calls_api:
  - endpoint-rides
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride
---

# Screen: finished

## What the screen knows before it asks (B-59)

R8 opens carrying the ride's own rating — `RideView.stars` — so a refresh or a pasted link does not
offer to rate the same journey twice. Nothing in the tip row is selected until somebody selects it:
`selectedTip == null` used to mean *skip*, which spent the screen's one accent surface, on opening,
recommending that nothing be paid.

The meta line is the kit's: `paid with <method> · <duration>`, the method being the id the request
carried rather than a card number this product does not have. And `total with tip` appears once a
chip is chosen — the fare plus the chip, which is arithmetic the screen already holds, not the
receipt's lines.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/FinishedViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/FinishedScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderFinished.kt` |
| Golden | `screens_rider_finished` |

## 0. Entry point and visibility

- **Entry point:** `/finished/{rideId}`.
- **Shown when:** the trip screen sees `COMPLETED`. Before B-44 that popped the trip and left the
  rider back at the picker — no sum, no rating, nowhere to put a tip.
- **Left when:** *done*, which clears the stack back to the picker.

## 1. What is on it

| Element | Where the value comes from |
|---|---|
| the sum | `RideView.chargedCents` — **what the settlement took**, not the quote |
| the destination and distance | the quote, which is what the journey was |
| five stars | local until *done* |
| three tips and *skip* | fixed amounts in cents, from `FinishedUiState.TIPS` |

**The sum is the capture and not the quote.** They are the same number for a fare and differ by three
quarters for a cancellation fee, so a screen showing the quote would be right until the first ride
that ended early.

**`skip` is a button the size of the others.** The common case is a rating and no tip; a product whose
refusal is smaller than its consent is doing something else.

**The stars are a vector, not `★`.** The bundled face has neither U+2605 nor U+2606 —
`GlyphCoverageTest` said so the moment the first version used them, and a character no bundled font
can draw falls back to whatever the host has, which is a different width and moves its neighbours.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/rides/{id}` | `Rides.ById` | [endpoint-rides](../api/endpoint-rides.md) |
| `POST /api/rides/{id}/rating` | `Rides.Rate` | [endpoint-rides](../api/endpoint-rides.md) |
| `POST /api/rides/{id}/tip` | `Rides.Tip` | [endpoint-rides](../api/endpoint-rides.md) |

**Nothing is sent until *done*.** Two taps on the stars would otherwise be two ratings, and the second
collides with the first — a rider rates a ride once, and the table's primary key is what says so. The
rating goes first and the money second: a refused rating must not swallow a tip the rider agreed to.

## 3. What it does not do

- No comment box: the artboard has none, and free text is a moderation problem this product would
  not solve.
- No "rate the passenger": D5 has it, it is the same shape in the other direction, and it waits for a
  reason beyond symmetry.
