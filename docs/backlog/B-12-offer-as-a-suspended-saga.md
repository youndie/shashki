---
id: B-12
title: "The driver offer is a suspended saga with a deadline, not a step that waits"
status: open
priority: P0
size: M
stage: stage-2-saga
blocked_by: [B-11]
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
  rating). That is B-11's EXECUTION step; this item is about what happens between offers.

- AC: three consecutive declines cascade without the saga holding a connection, provable from the
  pool's in-use count.
- AC: a deadline nobody answers rolls the saga back and frees the driver.
- Anchors: `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `petich/petich-scheduler`
