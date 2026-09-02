---
id: B-58
title: "cancellationReason is on the wire, read by the repository, and written by nobody"
status: open
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-58 — cancellationReason is on the wire, read by the repository, and written by nobody

`ServiceAreaStep` refuses with `InterceptorResult.Reject("pickup is outside the service area")`.
`RideView` carries `cancellationReason`. `PetichRideRepository` reads `data[Enriched.REJECTION]`.
`Enriched.REJECTION` is declared. **Nothing writes that key** — `grep` finds the constant twice, at
its declaration and at the read. Measured on the stand: a ride refused by that step comes back
`CANCELLED` with `cancellationReason: null`, and so does a ride that ran the whole cascade and found
nobody. The rider is told a ride was cancelled and never why.

- **This is the same shape as the three seams v1 found, in a fourth place** — built at both ends,
  joined at neither, and green all the way through because nothing asked whether the value was ever
  *written*. The fix is small: the step's message reaches the enriched payload, or the repository
  stops reading a key that does not exist and the field comes off the wire.
- **Which of the two is the decision.** A reason a rider can read is worth having — R5·a and R6·a in
  the kit are both a sentence — and the mechanism to carry one already exists on both sides. Removing
  the field would be honest and would throw the mechanism away.
- The alternative of inventing a reason at the read ("no cars nearby" whenever it is null) is worse
  than silence: it would say that about a ride refused for being outside the city.
- Deliberately **not** covered: distinguishing *why* the cascade found nobody — asked and declined,
  asked and expired, nobody to ask. That is a richer answer and a separate item.

- AC: a ride refused by the service area comes back with that sentence in `cancellationReason`, and
  the rider's screen shows it.
- AC: a test asserts the value survives the saga into `RideView`; the control is the same test
  against today's code.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderPayload.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSteps.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/data/PetichRideRepository.kt`
