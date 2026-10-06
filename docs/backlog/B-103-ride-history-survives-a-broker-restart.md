---
id: B-103
title: "The ride history stops for good when the broker restarts, and the subscription it reads can drop batches"
status: wip
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-103 — the ride history survives a broker restart, on a client that does not drop batches

`BooblikRideHistory` follows `ride-events` once and collects it with nothing around the collection.
booblik's subscription has no retry of its own: a closed connection or an `OFFSET_OUT_OF_RANGE` ends
the flow with an exception, the coroutine finishes, and the projection stops — silently, until the
server restarts (`server/.../events/data/BooblikRideHistory.kt`). A broker restart is routine: an image
bump, a node drain. The same shape cost konekt a week of dead live usage (its B-107).

Separately, the client it reads with had a defect of its own: `follow()` handed batches to a channel of
64 with `trySend` and ignored the result, so a collector more than 64 batches behind lost batches while
the position moved on (youndie/booblik M-170, fixed in #79). A projection replaying a long log at start
is exactly when a reader outruns its collector.

- **The decision:** booblik **0.3.5**, the first release with M-170; and a reader per partition that
  survives its connection — on failure it says so, waits a second and follows again **from the next
  offset of that partition**, not from `Earliest`, so a restart of the broker costs a reconnect rather
  than a replay of the log. `OFFSET_OUT_OF_RANGE` (retention passed the reader) resumes at the start of
  the live log with the gap logged — the projection's stated limit, now reachable without a restart.
- The projection stays idempotent by offset (`InMemoryRideHistory`), so a batch read twice across a
  reconnect is recorded once.
- Rejected: a stored position (booblik's `feature-consumer-position`) — this read model is rebuilt from
  the log on start by design (`RideHistory`'s KDoc), so it has nothing to store a position next to.

- AC: with the broker restarted under it, the history keeps receiving the events published after the
  restart, without a server restart; with the retry removed (the control) the same test is red.
- AC: shashki builds against booblik 0.3.5.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/events/data/BooblikRideHistory.kt`,
  `gradle/libs.versions.toml`.
