---
id: B-97
title: "A ride can be assigned and settled without anyone downstream being told"
status: wip
priority: P2
size: S
stage: stage-6-what-running-it-said
blocked_by: [B-96]
---

# B-97 — the announcement that fails and the saga that completes anyway

Two members of this system are announcements: `publish-assigned` in `OrderSteps.kt` and
`publish-settled` in `SettlementSteps.kt`. Both only call `ctx.emit`, which petich's README calls the
safe shape — and both begin by reading enrichment that has to be there:

```kotlin
driverId = ctx.enriched(Enriched.DRIVER_ID) ?: error("the announcement was reached with no driver"),
quote = ctx.quote() ?: error("the announcement was reached with no quote"),
```

If either `error(...)` fires, petich does not roll the saga back — by then the work is done, and that
is deliberate. It counts `onAnnouncementFailed` and carries on. The saga ends `COMPLETED`, the ride
is assigned, the money has moved, and **`RideAssignedEvent` never leaves the outbox because it was
never emitted**. Nothing downstream learns. Not late — never.

This engine is already built with `requireOutbox = true` and a `RefusingMetrics` that turns a dropped
event into an error, because this repository has already decided it does not tolerate a silent drop
of exactly this shape. The announcement path is the same drop with no such switch on it, and it was
not a decision: `AnnouncementFailureHandler` arrived one petich snapshot after the one this build is
pinned to (B-96).

## Acceptance

- An `AnnouncementFailureHandler` is wired, returning an outbox event that says the announcement for
  this saga could not be made — enough for the far side to ask rather than to guess. Its shape is
  this application's, which is why petich does not invent one.
- `PetichEngineConfig(requireAnnouncementFailureHandler = true)`, beside `requireOutbox`, so this
  cannot be unwired by a refactor without the engine refusing to start.
- `RefusingMetrics` records `onAnnouncementFailed`. It must **not** `error()` the way `onDroppedEvents`
  does: a dropped event is a wiring mistake that should never happen, a failed announcement is a
  delivery problem at runtime, and killing the pass over one would undo B-49's whole point.
- A test per announcement: the enrichment is missing, the saga still completes, and the outbox holds
  the failure event. Checked by mutation — removing the handler must fail it.
