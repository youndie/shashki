---
id: B-95
title: "The sweeper reports nothing, so a broken one and an idle one read the same"
status: done
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

## Findings

**Three of the six are wired and three are not, and the three that are not say why at the call site.**
`onWorkerFailure`, `onRevived` and `onUnknownType` go through `SweeperReport` to
`LoggerFactory.getLogger("shashki.sweeper")` — the same shape this repository already uses for
`shashki.simulator`, `shashki.positions` and `shashki.bundles`, rather than a channel invented for
this. `onExpired` is a notification this product does not send; `onContended` is worth a line on more
than one instance and this deployment is one; `onNotExpired` is the ordinary race of the poll
interval against the deadline, which is the path that exists to be lost safely.

**`warn` for the first two, `error` for the third.** One failed pass is the ordinary weather of a
background worker and what is worth paging on is the rate; a definition deployed away under live
sagas is a deploy that needs undoing.

**Nothing in `SweeperReport` may throw, and that is a property of the pinned version.** At
`0.4.0.97` — checked against petich's tree at commit `12d9028` rather than remembered —
`onWorkerFailure` is called from the `catch` that keeps the worker's loop going, so an exception out
of it ends the sweeper for the life of the process. petich's own B-55 later guards that callback and
splits the stages, but this build does not have it (B-96); the stage strings here are that version's:
`sweep` for the whole pass, `expire:<id>` and `stuck:<id>` per saga.

**The first test was the item's own defect, reproduced inside it.** It built its own
`SuspendedPetichSweeper` with the three callbacks passed, and **stayed green when they were deleted
from `Application`** — proving that `SweeperReport` works and nothing about whether anything calls
it. The composition now has a name, `sagaSweeper`, and the test builds the one the server builds.

**Against the real logger rather than a seam.** `SweeperReport` could have taken a lambda the test
supplies; then the test would prove something about the lambda. A `ListAppender` on
`shashki.sweeper` means the assertion runs through slf4j and the real logger name. It costs one line
in `server/build.gradle.kts`: logback was `runtimeOnly`, which puts it on the test runtime path and
not the compile one.

**Checked by two mutations.** Deleting the three callbacks fails all three tests; swapping
`onRevived` and `onUnknownType` fails two of them, with `re-drove saga ride-orphan after the process
running it died` naming the swap — so each callback is pinned individually rather than the three as
a group.

**Verification.** `./gradlew check` on the Linux box, exit code read rather than piped: green, 33
test classes. `make check` clean. The version was not touched — that is B-96.
