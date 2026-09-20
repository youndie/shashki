---
id: B-93
title: "A saga whose process died is never picked up, because stuckAfter was never set"
status: done
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

## Findings

**Ninety seconds, derived rather than chosen.** petich states the bound —
`stuckAfter > max(phaseTimeoutsMs ∪ compensationTimeoutsMs)` — and this application overrides neither
table, so both are petich's defaults and their maximum is AUTHORIZATION's **30 s**. The bound is per
**member**, not per pass: every member that proceeds writes, and the store stamps `updated_at` on
every write, so the gap the sweeper measures is one member's timeout. Ninety is three times it; the
margin covers the write following a member that took its whole timeout, plus the optimistic-retry
backoff (`2^n × 20 ms` over at most five attempts — about 1.5 s, negligible beside 60 s of headroom).
The derivation is written at the value, not here.

**`maxCompensationAttempts` is petich's default 3**, because this application configures only
`requireOutbox`. It matters now for the first time: a rollback can span passes, so
`KEY_RETENTION`'s inequality finally has both factors — 3 × 90 s is four and a half minutes against a
24-hour window, which it clears by two orders of magnitude. `PaymentGateway.kt` says so with both
numbers named, so raising either is visibly a change to that inequality.

**Writing the test found why it could not have been written before, and that is the larger half.**
`SagaStorage` passed no clock to `ExposedPetichRepository`, whose parameter defaults to the wall
clock — so `updated_at` came from `System.currentTimeMillis()` while the sweeper's threshold came
from the injected `PetichClock`. In production both are the wall clock and nothing shows. With a
controlled clock the two are **years** apart, the stranded query matches nothing, and it does so
silently: `sweepStuck()` returns 0, which is also what "nothing is stuck" returns.

So the fix is not a test that moves its own clock to meet the store. `SagaStorage` takes the clock,
and **without a default** — one was written first and `kapkan`'s wall-clock rule refused it, in as
many words: *a time that has to agree with somebody else's is a value carried in, not one read here.*
That is the defect restated by a rule that was already in the build. A default is what let two
sources of time into one mechanism without anybody choosing it.

**Two tests, and the second is the one that keeps the number honest.** A saga left in `PROCESSING` is
re-driven once the clock passes the threshold; a saga touched thirty seconds ago is **left alone**,
because re-driving one a live instance is still working on runs its members twice — which is the
failure the formula exists to prevent and the reason a smaller number would be wrong.

**One wrong turn worth recording:** the first version called `sweep()`, which counts expirations
only. The stranded queue is `sweepStuck()`. They are separate methods and a test that calls the wrong
one gets `0` — the same answer as a configuration that hides the saga, which is how this went a round
before the cause was clear.

**Verification.** `:server:build --rerun-tasks` on the Linux box, exit code read rather than piped;
the whole server suite passes with the three tests that now hand their storage a clock. Mutation
after the implementation was committed: taking `stuckAfter` away again — the state this item found —
fails the re-drive case.
