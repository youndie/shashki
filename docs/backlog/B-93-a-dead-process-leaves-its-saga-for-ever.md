---
id: B-93
title: "A saga whose process died is never picked up, because stuckAfter was never set"
status: wip
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-93 — A saga whose process died is never picked up, because stuckAfter was never set

`Application.kt` starts the sweeper as

```kotlin
SuspendedPetichSweeper(repository = storage.petiches, engine = engine, clock = get<PetichClock>())
```

`stuckAfter` is not given, and petich's default is `null` — which switches the stranded-saga half
**off**. So the sweeper here expires suspensions and nothing else: a saga whose process died mid-pass
sits in `PROCESSING` for ever, holding whatever it held.

petich has had that re-drive since B-26 and its README calls the number a formula rather than a
taste:

```
stuckAfter > max(phaseTimeoutsMs ∪ compensationTimeoutsMs)
```

— because there is no lease, so nothing distinguishes a dead process from a slow one, and setting it
too low re-drives a saga a live instance is still working on.

Found while closing B-91. It is why that item's retention arithmetic came out undefined: with the
re-drive off, a rollback lives inside one `process` call, so `maxCompensationAttempts × stuckAfter`
has a null factor. Switching this on changes that bound, which is why the two belong together.

## Acceptance

- `stuckAfter` is given a value derived from this application's own timeout tables, and the
  derivation is written where the value is, not in a commit message.
- A test that a saga abandoned in `PROCESSING` is re-driven and completes — the shape shashki's
  `dying after any phase` tests already stage, continued one step further.
- `KEY_RETENTION` in `PaymentGateway.kt` is re-checked against
  `maxCompensationAttempts × stuckAfter` once the product has both factors, and the finding says
  whether 24 hours still clears it.
- `maxCompensationAttempts` is stated too: it is petich's default of 3 today because this
  application configures only `requireOutbox`, and a rollback that can now span passes is the first
  thing that makes that number visible.
