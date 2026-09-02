---
id: B-31
title: "The wait for a car, which the kit puts on every class tile"
status: open
priority: P2
size: S
stage: stage-3-surface
---

# B-31 — The wait for a car, which the kit puts on every class tile

The kit's class tile reads `4 min · Kia Rio`: how long until a car arrives, and which car. The server
can answer neither, so [B-28](B-28-the-client-application-shell.md) draws both as a dash — on the
rule that a number in the wrong place reads as an answer and a dash reads as a question. This is the
item that gets the first half right.

- **Everything it needs exists and is not joined up.** `GeoCandidateSource` finds the nearest
  drivers of a class ([B-20](B-20-matching-geo-index-and-driver-simulator.md)) and
  `GraphHopperRouteEstimator` routes between two points
  ([B-23](B-23-routes-and-eta-on-embedded-graphhopper.md)). The wait is the second applied to the
  first, per class.
- **It belongs beside the quote, not in a second call.** `POST /api/quotes` already answers one road
  priced three ways; the wait is another field of the same answer, and a screen that asked twice
  could show a price for one moment and a wait for another.
- **The car is not part of this.** `RideView` carries a `driverId` and nothing about the vehicle —
  no model, no colour, no registration — and inventing them is worse than a dash, because the
  registration is what a rider checks a real car against. That is its own gap and its own decision
  about what the server should hold.
- The rejected alternative is a constant. A demo that says "4 min" whatever the map shows is a demo
  whose most-looked-at number is a decoration.

- AC: `POST /api/quotes` answers a wait per class, computed from the nearest candidate of that class
  and the router, and the class tile shows it.
- AC: a class with no candidate says so — the kit's tile already has the state, and it is the honest
  answer rather than an absent row.
- AC: a test in which two classes have candidates at different distances and the waits differ
  accordingly, so the number is shown to come from the map rather than from a constant.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/quote/QuoteRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/GeoCandidateSource.kt`
