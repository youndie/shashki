---
id: B-11
title: "The order saga on petich, with the outbox required rather than optional"
status: open
priority: P0
size: L
stage: stage-2-saga
---

# B-11 — The order saga on petich, with the outbox required rather than optional

Research §1.4 confirmed the brief's phase mapping is exact: `ENRICHMENT → VALIDATION →
AUTHORIZATION → EXECUTION → POST_PROCESSING` is what `PetichPhase` declares. It also found that the
outbox is opt-in — without `requireOutbox = true` an engine whose repository cannot store events
drops them, the saga completes, its state is correct, and nothing anywhere says an event was lost.

- **Turn it on at construction, and wire `onDroppedEvents` anyway.** The brief's whole
  post-processing story is the outbox publishing `ride-events` into booblik; a silent drop is the one
  failure this demo exists to make impossible.
- The rejected alternative is the default. It is the right default for an application with no broker
  and the wrong one for this.
- Covers the compensations too: rider cancellation is compensation from the middle of the saga, which
  is the same mechanism read backwards and the demo's point.

- AC: killing the process at each phase boundary leaves no held payment and no reserved driver.
- AC: `PetichEngineConfig(requireOutbox = true)`, and a test that a repository without outbox support
  fails to build an engine.
- Anchors: `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `petich/petich-postgres`
