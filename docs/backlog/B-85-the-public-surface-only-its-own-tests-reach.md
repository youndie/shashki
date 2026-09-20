---
id: B-85
title: "Twenty-four public declarations that nothing but their own tests reaches"
status: done
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-85 — Twenty-four public declarations that nothing but their own tests reaches

`kapkanJoins` — sborka's report of what a repository built and never called — ran here for the first
time with sborka 0.2.0.27 and read 1563 class files: **29 findings out of 333 types and 253 functions,
and 24 of them are the same shape.** A `public` declaration whose only caller is its own test:

| where | what |
|---|---|
| `server/…/billing/PaymentGateway.kt` | `Hold`, `activeHolds()`, `captured()` |
| `server/…/dispatch/` | `DriverSimulator` with `SimulatedBehaviour` and `SimulatorConfig`, `start()`, `DriverIndex.onlineCount()`, `DriverReservations.all()` and `reservedFor()`, `OfferBoard.forRide()`, `DroppedFrames.total()` |
| `server/…/feature/` | `RideAssignedEvent`, `RideSettledEvent`, `TripsTable`, `OfferTimeouts.pending()`, `DegradationCounter.count()` and `total()` |
| `shared-ui/…/map/` | `PlaceholderMapSurface`, `TileProjection`, `MemoryTileSource`, `MapSurface.emptyScene()`, `Projection.toGeo()` |
| `protocol/…/ScreenTokens.kt` | `ShashkiTokens` |

**This is not a request to delete twenty-four things, and treating it as one would be the wrong
reading.** The pile has at least three kinds in it and the item is to sort them, because each kind
has a different answer:

- **A test's window into a mechanism** — `activeHolds()`, `captured()`, `pending()`, `onlineCount()`.
  The production path never asks; the test asks because the alternative is asserting on nothing. The
  answer is probably to say so, either by narrowing the visibility to `internal` where the test is in
  the same module, or by naming it in the KDoc as an inspection point. Neither is a deletion.
- **A half of a mechanism that really is not joined**, which is the shape this repository has found
  four times ([B-32](B-32-which-screens-the-server-sends.md),
  [B-37](B-37-the-settlement-saga.md), [B-41](B-41-the-rider-actually-signs-in.md),
  [B-42](B-42-a-driver-is-reserved-for-life.md)). `Projection.toGeo()` is the candidate: it is
  declared on an interface, implemented twice, and no screen has ever turned a tap back into a
  coordinate. Either something should, or the interface promises something the product does not do.
- **Something a deployment reaches and the compiler cannot see** — `TripsTable` and
  `RideAssignedEvent` go out through Exposed and through the outbox, and the report says what it can
  see, which is class files.

- **The decision this item takes is which of the three each one is**, and the record is the point:
  a list read once and forgotten costs more than it saves, because the next run produces the same
  twenty-four and nobody knows which were already judged.
- The rejected alternative is twenty-four items. The findings are one question asked twenty-four
  times, and splitting them would bury the one or two that are real under twenty-two that are fine.
- The second rejected alternative is suppressing them. `kapkanJoins` is a report and not a gate — it
  fails no build — so a suppression buys silence and nothing else, and the honest form of "we looked
  at this" is a sentence in the file rather than an annotation that stops a task nobody is blocked by.
- Deliberately **not** covered: the five findings that are not "tests only". `RatingsTable`,
  `UnsentReceipts`, `Enriched`, `Settled` and `StreetLabel` are each used inside their own file,
  which is Kotlin's own idiom and what the rule's own documentation says it cannot tell from a defect.

- AC: every one of the twenty-four is classified in this file as an inspection point, an unjoined
  half, or reached outside the class files — with the reason beside it, in one line each.
- AC: whatever is decided to be an unjoined half becomes its own item, cited from here.
- AC: the inspection points that can be `internal` are `internal`, and `./gradlew check` is green
  after — which is the control that they were only ever reached from their own module.
- AC: `./gradlew kapkanJoins` after the work reports a smaller "only tests" pile, and this file says
  what the new number is — a report whose number nobody writes down is a report nobody reads twice.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/billing/PaymentGateway.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/Dispatch.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/MapProjection.kt`

## Findings

### The list was stale before it was worked, which is the first thing worth knowing

The item counted **29 findings, 24 of them "tests only"**. Running the report again read **35 and
25**. Six arrived from work done since it was filed: `OrderStep`, `OrderCheck`, `OrderAnnouncement`,
`SettlementStep`, `SettlementCheck`, `SettlementAnnouncement` — the member base classes the petich
migration created or renamed. Five of those six are the "used inside its own file" category this item
explicitly excluded, which means **the excluded category grows with every migration** and the number
that matters is the other one.

So a tally written once decays. The number below is dated for that reason.

### The twenty-five, sorted

**A test's window into a mechanism — the production path never asks (17).** The answer is narrower
visibility where the test is in the same module, and a sentence where it is not.

| declaration | why it is one |
| --- | --- |
| `PaymentGateway.Hold`, `activeHolds()`, `captured()` | the mock's read-backs; the saga writes, only a test looks |
| `DriverSimulator`, `SimulatedBehaviour`, `SimulatorConfig`, `start()` | a load fixture, never wired into the application |
| `DriverIndex.onlineCount()` | a count nothing routes on |
| `DriverReservations.all()`, `reservedFor()` | assertions about a map the saga writes through two other methods |
| `OfferBoard.forRide()` | `forDriver` is the one a route calls; this is the mirror a test needs |
| `DroppedFrames.total()`, `DegradationCounter.count()`, `total()` | counters whose production reader is a log line, not a caller |
| `OfferTimeouts.pending()` | the cascade's timers, visible only to the test that asserts one exists |
| `MapSurface.emptyScene()` | the empty state a screenshot test renders |
| `TileProjection`, `MemoryTileSource` | the single-tile picture the goldens are still drawn from |
| `PlaceholderMapSurface` | the same, and **cross-module** — see below |

**An unjoined half (1).** `Projection.toGeo()` — declared on the interface, implemented twice,
correct in both, and called by nothing outside a test, while `toCanvas` is called by every screen that
draws a marker. No screen lets a rider pick a place by touching the map. Filed as **B-94** with the
two ways out and their sizes. It is a `shared-ui` matter and has nothing to do with the saga engine,
which B-94 now says outright — the prior instances of this shape are *this* repository's B-32, B-37,
B-41 and B-42, and those numbers mean something else entirely in petich's backlog.

**Reached outside the class files (3).** `RideAssignedEvent`, `RideSettledEvent` — serialised into
the outbox and read by a relay; the Kotlin type is local, the **wire contract is not**, and narrowing
it would say the opposite of what is true. `TripsTable` — reached by Exposed and by the migration,
which is why `SchemaTest` exists; narrowed anyway, because Exposed reads the object from inside the
module.

**Cross-module, and the control is what said so (1).** `ShashkiTokens` is named in three modules'
sources. And `PlaceholderMapSurface` is reached from **`driver`'s** tests — `internal` failed to
compile on it and nothing else, which is a sharper answer than reading could have given: "only tests
reach it" and "only its own module reaches it" are different facts, and the report says the first.

### What was narrowed, and the control

Six are `internal` now: `DriverSimulator`, `SimulatedBehaviour`, `SimulatorConfig`, `TripsTable`,
`MemoryTileSource`, `TileProjection`. `./gradlew check` is green after, which is the control the
acceptance asked for — they were only ever reached from their own module. A seventh,
`PlaceholderMapSurface`, was tried and reverted; the compiler named it.

The rest cannot be narrowed for reasons rather than by preference: an interface member cannot reduce
visibility below its interface's (`activeHolds`, `captured`, `forRide`, `all`, `reservedFor`,
`onlineCount`, `pending`, `toGeo`, `emptyScene`), and the two events are a wire contract.

### The tally, dated

**2026-09-20, after this item:** `./gradlew kapkanJoins` reports **29 findings, 19 of them "tests
only"** — down from 35 and 25. The six that moved are the six narrowed. Nothing was deleted and
nothing was suppressed.

A run that reports more than 19 has found something new; a run that reports 19 has found the same
pile, already judged, and needs no second reading until this file is out of date again.
