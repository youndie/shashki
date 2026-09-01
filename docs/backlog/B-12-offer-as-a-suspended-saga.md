---
id: B-12
title: "The driver offer is a suspended saga with a deadline, not a step that waits"
status: done
priority: P0
size: M
stage: stage-2-saga
---

# B-12 — The driver offer is a suspended saga with a deadline, not a step that waits

Research §1.4a: EXECUTION's default timeout is 10 000 ms and the offer is 15 s, cascading to the next
driver on a decline. A blocking implementation is correct exactly until the first driver ignores an
offer — which is the normal case, not the edge case.

- **Use the mechanism petich already has**: a saga that pauses for a human, holding neither a thread
  nor a database connection, with a deadline swept by a background job.
- The rejected alternative — raising EXECUTION's timeout — moves the number without fixing the shape:
  a thread and a connection are still held for the length of a cascade.
- Not covered: the matching itself (nearest N online drivers of the class, sorted by distance and
  rating). That is [B-20](B-20-matching-geo-index-and-driver-simulator.md), which also supplies the
  simulated drivers this item's acceptance needs — three that decline, one that never answers; this
  item is about what happens between offers.

- AC: three consecutive declines cascade without the saga holding a connection, provable from the
  pool's in-use count.
- AC: a deadline nobody answers rolls the saga back and frees the driver.
- Anchors: `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `petich/petich-scheduler`

## What it turned out to be

Built against the stub candidate list, as research §1.4d said it could be; B-20 replaces the list and
supplies drivers that misbehave on purpose, and nothing here changes shape when it does.

**EXECUTION is two steps, and the split is petich's grammar.** `OfferStep` reserves the nearest
candidate, posts the offer to a board the driver's app polls, and returns `Suspend` — the saga is
parked in the database holding neither a thread nor a connection. A suspended step is not re-run on
resume, so the driver's answer lands in `DriverAnswerStep`, the next step of the same phase: `ACCEPT`
proceeds; `DECLINE` and `IGNORED` free that driver, ask the next candidate and `Resuspend`, which
keeps the saga at *this* step so the next answer lands here too; nobody left is `Compensate` — the
hold released, the rider told "no cars nearby". A rider's cancellation is a resume payload that
becomes the same `Compensate` with a different reason: compensation from the middle, D5 as code.

**Two deadlines, deliberately different, because petich's expiry is a rollback and not a cascade.**
Read in `expireSuspended`: an expired suspension goes to `triggerCompensation`, full stop. So the
fifteen seconds one driver gets are the application's — `OfferTimeouts`, an in-process timer that
resumes the saga with `IGNORED` and moves the cascade on — and the `ttl` handed to petich is the
whole matching budget, ninety seconds (the kit's R5 → R5·a), swept by `SuspendedPetichSweeper`. The
gap is named in the class: after a restart the timers are gone, the sweeper's budget is what
survives, and an unanswered offer is then rolled back rather than cascaded. That is the right
degraded behaviour; petich-scheduler would buy back the cascade across restarts and nothing else.

- ~~AC: three consecutive declines cascade without the saga holding a connection, provable from the
  pool's in-use count.~~ Done — `OfferCascadeTest` reads `hikariPoolMXBean.activeConnections`
  between every offer and it is zero; the fourth answer has nobody left and compensates.
- ~~AC: a deadline nobody answers rolls the saga back and frees the driver.~~ Done — the clock is
  moved past the budget, `sweep()` finds one, and the reservation, the hold and the board entry are
  all gone.

Also held by tests: an accept assigns and leaves the hold for the settlement; an ignored offer moves
on like a decline; an answer from a driver who was not asked changes nothing; the rider's cancel
while a driver is asked compensates, and cancelling an assigned ride is a 400 — that is the trip's
business (research §1.4c). The routes: `GET /api/driver/offers/{driverId}`,
`POST /api/driver/offers/{rideId}/answer`, `POST /api/rides/{id}/cancel`, all public until B-09 and
saying so.

**Two corrections the run made.** `Compensate` from a resumed step ends `FAILED` in petich's
vocabulary — `REJECTED` is a `Reject` before anything happened — and the rider sees `CANCELLED`
either way. And the recovery test from B-11 now sees what a fresh engine really does with a row
parked at EXECUTION: it asks a driver and parks, with the one hold it already had, and the answer
finishes it.

**A method note, because it bit twice in one evening.** Two scripted edits anchored on lines that
ktlint's formatter had since reflowed — one in a test, one in `StatusPages` — and `str.replace`
matched nothing and said nothing. The second left `OfferNotFoundException` imported and unmapped,
which surfaced as a 500 where a 404 was asserted. An edit anchored on formatted code is an edit to
verify by reading the result, not by the absence of an error.
