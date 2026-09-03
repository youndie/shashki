---
id: screen-driver-trip-summary
title: Trip complete
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/driver/summary/{rideId}"
parent_feature: feature-settlement
calls_api:
  - endpoint-driver
source: driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip
---

# Screen: trip complete

The kit's D5. **The figure is what he earned, not what the passenger paid, and the fee is shown,
never hidden** — the design's own sentence, and the screen's whole argument.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripSummaryViewModel.kt` |
| Screen / Content | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/ui/TripSummaryScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverTripSummary.kt` |
| Goldens | `screens_driver_trip_summary`, `screens_driver_trip_summary_light` |

## 0. Entry point and visibility

- **Entry point:** the trip screen, when the ride reaches `COMPLETED`. The summary **replaces** the
  trip on the back stack rather than being pushed over it, so back from here does not offer to
  drive a finished ride; the one action returns to the shift.
- **Address:** `/summary/{rideId}` inside the driver bundle.

## 1. Screen states

- **Loading:** an ellipsis. The settlement is a saga and can be a moment behind the `COMPLETED`
  that opened this screen, so the view model asks up to five times, a second apart, before giving
  up — *not yet* and *not at all* are told apart by whether it is still asking.
- **Content:** the figure, its meta line, the lines, the kit's sentence and the bar.
- **Not written yet:** "the payout is not written down yet", after the retries. There is no button
  to retry by hand: the offer loop is still running underneath and the shift is one tap away.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/driver/rides/{rideId}/summary` | `DriverRides.Summary` → `TripSummaryView` | [endpoint-driver](../api/endpoint-driver.md) |

## 3. What the numbers are

| Line | Where it comes from |
| :--- | :--- |
| `+$ 26.17` | `payoutCents + tipCents` — the payout rows, formatted; nothing is multiplied here |
| `card-4417 · today $ 46.32` | the ride's payment method and `todayCents`, D6's own sum |
| `fare` | what the rider was charged for the ride |
| `service fee 20 %` | `fareCents - payoutCents`, named with the percentage the server sent |
| `tip` | the tip's payout row, only when there is one; the driver keeps all of it |
| `35 min · 22.8 km` | the quote's leg, as the kit's last line has it |

**The share is the payout row and not `fare × 80 %`.** The two agree until the first rolled-back tip
or a changed commission, and then the recomputed figure is the driver's word against the bank's —
the same rule D6 states for its sums.

## 3a. D4·a — passenger cancelled (B-80)

The same screen with another first line. A rider who cancels after the car has set off is charged
the fee, the settlement pays the driver the fee's share, and the trip screen — which leaves on any
terminal status — lands here with `passenger cancelled` above the figure and `cancellation fee`
where `fare` would be. `TripSummaryView.cancelled` says which; the money is the same payout row.

## 4. What is deliberately not here

- **Rating the passenger.** The kit has *rate anna*; the protocol has no rider rating, and inventing
  one for a screen would be a second rating system nobody asked for.
- **"settled tonight".** When a payout is paid is a fact about a payment run this product does not
  have; the meta line names the card and today's total instead of promising a time.
