---
id: B-99
title: "RefusingMetrics throws from onDroppedEvents, and petich has swallowed that throw since B-52"
status: done
priority: P3
size: XS
stage: stage-6-what-running-it-said
blocked_by: []
---

# B-99 — a refusal that petich now makes silent

`OrderSaga.kt`'s `RefusingMetrics.onDroppedEvents` calls `error(...)`, and its KDoc says why: it
"cannot fire while `requireOutbox` holds … if it ever does, something has changed under this
constructor, and a counter nobody reads is not the place to find out."

petich B-52 (in every snapshot since `0.4.0.106`) wraps the metrics an engine is given in
`GuardedMetrics`, which catches everything but cancellation from every counter **and reports it
nowhere** — its own KDoc: "a counter that throws has nothing left to report to". So the throw this
server relies on to be loud is caught and dropped, and the comment describes a behaviour the engine
no longer has. Found while wiring B-98; read in petich's `Guarded.kt`, not yet run here.

- **The decision:** make the loud path real — log at `error` and count it through metrik — or delete
  the throw and let `requireOutbox` at construction be the whole guard, and say so in the comment.
- No test here exercises the throw (`grep onDroppedEvents server/src/test` is empty), which is how it
  went unnoticed.

## Acceptance

- The comment on `RefusingMetrics` says what actually happens when `onDroppedEvents` fires, and a
  test makes it fire through the engine and asserts that.

- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSaga.kt`

## Findings — 2026-10-06

- **Done, the loud path made real.** `RefusingMetrics` is `SagaMetrics` now: `onDroppedEvents` logs
  at ERROR on `shashki.saga` instead of throwing, and its KDoc says what happens and why — the saga
  completes, the event is lost, the line is what leaves the process. Counting it through metrik was
  the other half of the option; not done, because the ERROR line already reaches tracy and a counter
  of an event `requireOutbox` makes impossible would read zero for ever.
- **The swallow is measured now, not only read.** `SagaMetricsTest` runs a real engine over a
  repository with no outbox: the saga completes and exactly one ERROR line names the type and count.
  With the old `error(...)` restored, the saga still completes and the log is empty — petich's
  `GuardedMetrics` caught the throw, as the item said from reading `Guarded.kt`.
