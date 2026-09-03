---
id: B-85
title: "Twenty-four public declarations that nothing but their own tests reaches"
status: open
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
