---
id: B-70
title: "The driver finishes a trip and is shown nothing: D5 does not exist"
status: done
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-70 — The driver finishes a trip and is shown nothing: D5 does not exist

Live on the desktop driver against the stand (2026-09-03): *finish* is tapped, the trip screen pops,
and the next frame is D2 *waiting*. The kit's **D5 — trip complete** is a whole screen: `+420 ₽` at
54 in the accent, `card · settled tonight · today 4 700 ₽`, then `fare 470 ₽ / service fee 12% −50 ₽
/ 26 min · 18.4 km`, *rate anna*, and "Next offer arrives automatically. You stay online." Its own
note is the design's argument: **the figure is what he earned, not what the passenger paid, and the
fee is shown, never hidden.**

- The number exists. `PayoutRepository` holds the driver's share and D6 sums it; what is missing is
  the moment it is shown for the ride that just produced it.
- The rider's R8 is the mirror of this screen and it is built; a product that shows one side its
  money and not the other is telling half the story of the settlement.
- Deliberately **not** covered: rating the passenger. Nothing in the protocol carries a rider rating
  and inventing one for the screen would be a second rating system.

- AC: after `COMPLETED` the driver sees the payout for that ride, the fare it came from and the
  platform's cut, before returning to the shift — and returns to it on one tap or after a delay,
  as the kit says.
- AC: a golden of D5 on both themes, and the figure on it is the payout row's, not the fare.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/trip/`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/App.kt`,
  `docs/screens/screen-driver-assigned-ride.md`

## What it turned out to be

**A route, a screen and a replacement on the back stack.** `GET /api/driver/rides/{rideId}/summary`
answers `TripSummaryView` built from the payout rows: the driver's share as it was written down, the
fee as the fare minus that row, the tip's row on top, and D6's *today* sum with this ride in it.
`COMPLETED` now replaces the trip with `Summary(rideId)` — the mirror of what B-44 did for the rider
— and the one bar returns to the shift.

**The figure is the payout row, and that is asserted against the settlement rather than against
arithmetic.** `SettlementTest` drives a trip to its end, tips it, and compares `payoutCents` with
`PayoutRepository.find(ride.id)` and `feeCents` with `fare − payout`; a summary that multiplied the
fare by eighty per cent would pass today and drift the day the commission moves. `Commission.DEFAULT`
used to be a constructor default in two places and is bound once now, because a third reader was one
copy too many.

**The settlement is a saga, so the screen asks again.** The payout is written by the settlement's
execution phase a moment after the `COMPLETED` that opens the screen; the first read can land in
between and get 404. `TripSummaryViewModel` retries five times a second apart and then says so —
`TripSummaryViewModelTest` holds both halves, the wait and the giving up.

**The 400 that was a 404.** The first cut called `driverIdentity(null)` and answered 400 on the
provider-less stand, because with no token there is no driver at all. The resource carries the same
`driverId` seam every other driver route has (B-52): ignored the moment there is a token, the only
source there is without one.

**Not built, and named in the screen document:** rating the passenger — there is no rider rating on
the wire — and *settled tonight*, which is a promise about a payment run this product does not have.
The figure is set in the driver's amber: **9.95:1 on black, measured off the golden** (the kit's
sheet says 11.14, which is the same colour under a different rounding of the same formula), and on
the light theme the kit's own 2.11:1 on white that B-48 measured and recorded as the design's choice
rather than this product's.
