---
id: B-43
title: "The rider sees the wait and its end: matching, no cars nearby, and cancel"
status: open
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
