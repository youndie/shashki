---
id: B-31
title: "The wait for a car, which the kit puts on every class tile"
status: done
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

## What it turned out to be

**Nothing had to be built; two things had to be joined.** `GeoCandidateSource` already answered "who
is near, of this class" and `GraphHopperRouteEstimator` already answered "how long from here to
there". `PickupEta` is the second applied to the first, and the whole of it is fifteen lines.

The one thing that had to move is that `DriverCandidate` now carries **where** the driver is. The
index computed the position to sort by it and threw it away; asking the index again afterwards would
have made the ordering and the wait two answers about two different moments.

**The nearest by straight line, routed — not the fastest by road**, and that is a decision rather
than a shortcut. Routing every candidate is one graph search per online driver per class for a number
shown before anybody has ordered anything, and the index's own ordering is the one the offer cascade
uses: the rider is told about the driver they would actually be offered.

**`null` is an answer and appears twice.** No candidate of that class is the kit's "no cars nearby".
So is a candidate the router cannot reach — a driver beyond the extract's boundary, where B-23's own
note says out-of-bbox and far-from-road are indistinguishable. Neither is a number, because the wait
is the most-looked-at figure on that screen and a constant there is a decoration.

**Unavailable means no car, not no price.** Pricing is arithmetic and answers for all three classes;
what a rider cannot do is order a class nobody is driving. So the tile greys out on the wait rather
than on the fare, the view model refuses to select it — the kit's tile draws the unavailable state
and still reports a click, which is the component being a component — and the opening selection moves
to a class that has cars rather than leaving a greyed row with the order bar live under it.

**The test tells the road from a straight line, not just far from near.** The fixture graph is an L:
a car at the north end is 3 772 m away by road and 2 710 m by hypotenuse, against 774 m for a car
along the same way. A wait from the index's straight-line metres gives a ratio of 3.5 between the two
classes; a routed wait gives 4.9; the assertion sits at 4.2. Replacing the route with the index's own
distance produces exactly 3.52 and fails, which is what says the number comes from the map.

A third test is the control: with nobody online at all every wait is absent, so the numbers in the
first two came from the drivers rather than from the journey being priced.

**The car half stays a dash**, as the item said it would. `RideView` carries a `driverId` and nothing
about the vehicle, and the registration is what a rider checks a real car against.
