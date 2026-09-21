---
id: B-97
title: "A ride can be assigned and settled without anyone downstream being told"
status: done
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

## Findings

**The event is keyed by the ride, not by the saga, and finding that out was most of the work.**
Every outbox event this server publishes is keyed `"<rideId>:<suffix>"`, and `BooblikRideHistory`
takes the ride back out with `substringBeforeLast(':')`. The order saga's id **is** the ride id, so
an event built from `petich.id` would have looked right in every test written against it — and the
settlement saga's id is `"<rideId>:settlement"` or `"<rideId>:tip"`, which `substringBeforeLast`
turns into `"<rideId>:settlement"`: a ride nobody has. The handler asks the payload instead, and
`AboutARide` is the interface that makes both payloads say the thing they both already carried.
`a settlement nobody could announce is keyed by the ride rather than by the saga` is that half, and
`SettlementSagaTest`'s fixture proves it independently — its saga id is `s-announcement-dies`, which
shares nothing with its ride id.

**The reason is logged and not published, and petich's own B-57 is why.** `reason` is an exception's
message; `publish-settled` sends a receipt, so an SMTP failure names the recipient — which is
`riderEmail`, sitting two fields away in the payload the saga carries. The outbox goes to a broker
and out to whoever reads the topic; the log stays on this server. So `SagaAnnouncementFailedEvent`
carries a ride id, a saga id, a saga type and a member key — this server's own vocabulary — and `the
published fact carries no exception message` is what holds that.

**`requireAnnouncementFailureHandler` has no test of its own, deliberately.** One that built its own
engine would assert petich's guard and nothing about whether this server uses it — the exact shape
B-95 had to correct two items ago. What the flag buys was checked by mutation instead: take the
handler out of `sagaEngine` and leave the flag, and **every saga test in this repository fails at
construction** with petich's own sentence. A guard that cannot be unwired quietly does not need a
test of its own; it needs the rest of the suite.

**`onAnnouncementFailed` counts and does not throw, unlike its neighbour.** `onDroppedEvents` throws
because `requireOutbox` is meant to make it impossible, so its firing means something changed under
the constructor. A failed announcement is a delivery problem at runtime — a broker down, an SMTP
server not answering — and killing the pass over one would undo exactly what petich B-49 exists for:
the work is already done and the saga must finish.

**Two existing tests said the thing this item stops being true.** `a ride whose announcement dies
keeps its driver and its hold` and `a settlement whose announcement dies keeps the money where it
moved it` both asserted an empty outbox, with the message *"the event is the only thing missing"*.
That sentence was accurate and is now wrong, so the sentence and the assertion moved together rather
than the assertion alone.

**Checked by two mutations.** Removing the handler and the flag fails three of the four new tests
(`expected: <[saga.announcement-failed]> but was: <[]>`); removing only the handler fails every saga
test in the repository at engine construction.

**Verification.** `./gradlew check` on the Linux box against a real Postgres, exit code read rather
than piped and the result files' timestamps read rather than the log: green, 34 test classes — one
more than before. `make check` clean.
