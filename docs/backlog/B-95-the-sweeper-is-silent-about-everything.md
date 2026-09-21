---
id: B-95
title: "The sweeper reports nothing, so a broken one and an idle one read the same"
status: wip
priority: P2
size: S
stage: stage-6-what-running-it-said
blocked_by: []
---

# B-95 — six callbacks, none of them wired

`Application.kt` starts the sweeper with four arguments:

```kotlin
SuspendedPetichSweeper(
    repository = storage.petiches,
    engine = engine,
    clock = get<PetichClock>(),
    stuckAfter = STUCK_AFTER,
).start(this)
```

`SuspendedPetichSweeper` takes six more — `onExpired`, `onUnknownType`, `onRevived`, `onContended`,
`onNotExpired`, `onWorkerFailure` — and every one of them defaults to a no-op. So none of what this
worker does or fails to do is recorded anywhere in this application.

**`onWorkerFailure` is the one that matters most**, and petich's own documentation for it says why:
the worker survives a storage failure deliberately, because the work is not going anywhere and the
next pass picks it up — but *surviving is not the same as being invisible*. **A sweeper whose storage
has been refusing every pass for an hour looks EXACTLY like an idle one from outside**, and that is
the only state in which it is silently doing nothing. petich has no logger; this application has one.

**`onRevived` is the second.** It counts sagas picked up after the process running them died — a rate
that is normally zero, and whose becoming non-zero says instances are dying mid-saga. Nothing else in
this system is in a position to notice that. B-93 switched `stuckAfter` on and gave the sweeper that
job; nobody is reading the answer.

`onContended` is worth a line on more than one instance and nothing on one. `onExpired` is the
"your confirmation window has passed" hook, which this product does not currently need.
`onUnknownType` should be loud: it means a definition was deployed away under live sagas.

## Acceptance

- `onWorkerFailure` reaches the application's own logging, with the stage string petich passes
  (`sweep`, `stuck`, `expire:<id>`, `stuck:<id>`) — it names which queue and which saga.
- `onRevived` and `onUnknownType` are recorded too, and the reason each was chosen is in the wiring
  rather than here.
- A test that the failure reporter is actually called: a repository whose query throws, one pass, and
  the recorded line. Not "it compiles with a lambda".
- What is deliberately left unwired says so at the call site, so the next reader knows it was a
  choice.
