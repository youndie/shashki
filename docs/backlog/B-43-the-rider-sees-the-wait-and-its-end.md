---
id: B-43
title: "The rider sees the wait and its end: matching, no cars nearby, and cancel"
status: done
priority: P0
size: M
stage: stage-5-the-rest-of-the-kit
---

# B-43 — The rider sees the wait and its end: matching, no cars nearby, and cancel

The server already produces every state on these three artboards. `MATCHING` is the saga between
`POST /api/rides` and an accepted offer; an exhausted cascade ends in `CANCELLED` with no cars and
the rider "is told there are no cars" — in a test (`SimulatedCascadeTest`), not on a screen; and
`POST /api/rides/{id}/cancel` runs the two mechanisms §1.4c describes. The rider bundle draws none
of it: `RiderClassPicker` posts the ride and the trip screen polls it, so between the two the rider
is looking at a trip screen with no car, and the difference between "still looking" and "nobody
came" is invisible.

- **Three artboards, one screen with states.** R5 (matching: progress dots, no map, 4.4 s cycle),
  R5·a (no cars nearby: the 54/200 headline, *try again* / *notify me*, back to R4 keeps the address
  and the class) and R10 (cancel: confirmation and the fee rule) are one `RiderMatching` screen with
  a state per artboard, because they share the ride and differ only in what the server said.
- **Cancel shows the number before the button.** R10's copy is the fee rule, and the fee is known —
  a quarter of the fare after `ASSIGNED`, nothing before. The screen asks the ride which side of
  `ASSIGNED` it is on and says so; a confirmation that hides the amount is a dark pattern in a
  product that exists to show the seam.
- **"notify me" is out, and the button says so by not being there.** It needs a subscription and a
  push, neither of which exists; drawing it disabled is a promise. R5·a ships with *try again* only,
  and the research notes the omission beside the artboard.
- The rejected alternative is a spinner on the trip screen. It is what a rider sees now, and it is the
  one state the kit refused to draw as a spinner.

- AC: three goldens — `rider_matching`, `rider_no_cars_nearby`, `rider_cancel_confirm` — against the
  kit's R5, R5·a and R10, in both themes ([B-48](B-48-light-goldens-for-every-screen.md) once it lands).
- AC: with the simulator set to decline, ordering a ride in the browser lands on *no cars nearby*
  within the cascade's own deadline, and *try again* returns to a class picker still holding the
  address and the class.
- AC: cancelling before `ASSIGNED` shows no fee and releases the hold; cancelling after shows the fee
  and captures it — the two paths of `feature-settlement` reached from a screen rather than a test.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/`,
  `docs/screens/`

## What it turned out to be

**The server produced every state, and the one it does not produce is the one the screen needed.**
`MATCHING` is a status, `ASSIGNED` is a status, and `CANCELLED` is *two events wearing one status*:
the cascade ran out of drivers, and this rider pressed cancel a moment ago. Nothing on the wire tells
them apart — the saga records no rejection reason for a compensated order — and only the client can,
because it is the one that pressed. `MatchingUiState.cancelling` is set before the call and never
lowered, which is what stops a rider who cancelled from being shown "no cars nearby": a screen
blaming the city for their own decision.

**R10 needed a number, and the number could not be the client's.** The fee is a quarter of the fare
once a driver has set off — `Commission`'s rule, the one the settlement charges — and a screen that
multiplied by 0.25 itself would be a second copy of a pricing rule, drifting the first time somebody
moved the coefficient. So `RideView.cancellationFeeCents` is on the wire: `0` while the saga is still
asking, the fee once a driver is assigned, `null` once the rider is in the car and there is nothing
to confirm. Three answers, one test, and the confirmation shows the amount before the button.

**Verified on the stand, not only in tests.** A driver online who never answers: the rider's screen
went to *looking for a car* with the kit's dots, the driver's showed the offer card counting down
from fifteen, nobody answered, the cascade ran to its ninety-second budget, and the rider landed on
**no cars nearby**. *try again* came back to the picker with the address, the class and the payment
still on it. The screenshots are in the session; the parts a still cannot hold — the 4.4-second dot
cycle — belong to the kit's own test.

- AC 1 (three goldens): `screens_rider_matching`, `screens_rider_no_cars_nearby`,
  `screens_rider_cancel_confirm`. Light variants when [B-48](B-48-light-goldens-for-every-screen.md)
  lands, as the item says.
- AC 2 (a decline cascade ending on the screen): live, above. The decline half of it —
  every candidate refusing rather than one candidate ignoring — stays `SimulatedCascadeTest`'s,
  because a second driver is a second window.
- AC 3 (cancel before and after): the fee's three values are a server test
  (`the ride carries what cancelling it would cost right now`), the copy that shows them is a view
  test, and the two settlement paths were already `SettlementTest`'s. What is new is that both are
  now reachable from a screen — the trip screen asks before it cancels, which it did not do before.
- What is deliberately not here: *notify me*, which needs a subscription and a push. The button is
  absent rather than disabled, because a disabled button is a promise.
