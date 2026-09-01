---
id: B-23
title: "Routes and ETA through GraphHopper embedded in the server"
status: done
priority: P1
size: M
stage: stage-2-saga
---


**Unblocked:** [B-06](B-06-city-extract-and-tiles.md) is done and the graph imports in under four
seconds, so the extract this item routes on exists.
# B-23 — Routes and ETA through GraphHopper embedded in the server

Every figure the kit puts on a screen that is not a price is a route: the ETA beside a class tile,
the "4 min" on an offer, the line the rider watches the car travel along. Research §1.6 verified
GraphHopper as a Java library under Apache-2.0, which is what lets it live inside the one Ktor
process rather than beside it; the brief chose it for that reason. It has no item, and ENRICHMENT —
the saga's first phase, where the quote is computed — is its first consumer.

- **Embedded, on the same extract as the tiles.** One OSM file from [B-06](B-06-city-extract-and-tiles.md)
  produces both the pmtiles archive and the routing graph, so the road the car is drawn on is the
  road it was routed on. The import runs at server start from a cached graph directory; the first
  start pays the import, the rest do not. B-06 measured what that first start costs on this city:
  **under four seconds** for 98 566 nodes and 110 853 edges, 13.7 MB of graph on disk, GraphHopper
  11.0 with the stock car profile — small enough that a cold container is not a reason to keep the
  graph directory anywhere clever.
- **The route is a protocol type, not a GraphHopper type.** The server returns a polyline, a
  distance and a duration; the client's map interface (B-01) draws whatever polyline it is given.
  Nothing from `com.graphhopper` crosses the module boundary, so the router can be replaced without
  the protocol noticing.
- The rejected alternative is straight-line distance and a speed constant. It is what B-20's
  candidate query may use for the first sort, because it is cheap; it is the wrong number to put on
  a screen beside a price.
- Not covered: traffic, turn-by-turn instructions, or the driver's navigation view. The driver app
  shows the route and the next manoeuvre is out of scope for the reference.

- ~~AC: `POST /routes` with two points inside the city returns a polyline, metres and seconds; the
  same request against a cached graph answers in under 50 ms on the Linux box.~~ **Done,
  2026-09-02.** `POST /api/routes` — under `/api`, like everything else this server speaks. On the
  city's extract: **2.57 ms median, 6.6 ms worst of 201**, after a 50-route warm-up, against a
  50 ms bar. Centre to airport is 22 806 m over 364 points.
- ~~AC: the ENRICHMENT step of the order saga takes its distance and duration from this endpoint,
  and a class tile's ETA matches the route's duration for the nearest candidate.~~ **Done,
  2026-09-02.** `QuoteOnRoadsTest` asks for a route and then for a ride over the same two points and
  requires the quote's metres and seconds to equal the route's — plus that the distance exceeds the
  straight line by a fifth, so the test cannot pass with routing switched off.
- ~~AC: the simulator ([B-20](B-20-matching-geo-index-and-driver-simulator.md)) moves its cars along
  routes from this endpoint, not along straight lines.~~ **Done, 2026-09-02, with a control.** The
  simulator asks `POST /api/routes` the way a driver's application would and walks the geometry it
  gets back. `SimulatorFollowsRoadsTest` runs the same driver twice over the same fixture — once with
  the graph, once with the straight-line stand-in — and compares: on roads the car never leaves them,
  by line it wanders three times further.
- Anchors: `server/build.gradle.kts`, `server/src/main/kotlin/io/github/youndie/shashki/server/`

## What it turned out to be

**The routing was the easy half; making the tests mean something was the work.**

GraphHopper 11 embeds in four lines — profile, encoded values, OSM file, graph directory — and
answers a route across Ljubljana in 2.57 ms. Every difficulty was in arranging tests that a broken
version would fail.

**The fixture is an L, and that shape is the whole argument.** Four nodes and two ways: the road from
the west end to the north end goes east and then north, so the straight line between them is the
hypotenuse and is measurably shorter. Every assertion is about that difference, because "a number
came back" is what a router returning great-circle distance also produces. The real city's extract
is 41 MB and not in git; what it measures is a separate, opt-in run whose numbers are above.

**Three things about GraphHopper cost time and are worth keeping.**

- *A comment above `<osm>` fails the import.* `OSMInputFile.openXMLStream` calls `next()` exactly
  once and requires that event to be the `osm` start element, so a comment in the prolog produces
  "File is not a valid OSM stream" — a message naming neither the comment nor the line. The fixture
  carries its explanation *inside* the element, with a note saying why.
- *`minNetworkSize` defaults to 200* and drops connected components smaller than that, which is
  right for a city and deletes a four-node fixture entirely.
- *Out of the bounding box and far from a road are different failures that read the same.* A point
  outside the graph's box is refused before any snapping — "Point 0 is out of bounds" — while a point
  inside it snaps from 1 500 m away. This is in research §1.6e because it is the kind of thing that
  makes a test pass for the wrong reason, which is exactly what it did here: the simulator's drivers
  were wandering outside the fixture's box, getting no route, and falling back to straight lines. The
  test then measured straight lines against straight lines.

**The control is what caught it, and the control needed fixing too.** The first version required both
runs to reach the road before measuring — but the straight-line car never reaches it, which is the
point of the control. So the window is chosen by the clock, the same second half of each run, rather
than by the thing under test.

**Two small decisions recorded rather than left implicit.** The estimate carries the geometry instead
of a second `Router` interface beside it: two abstractions over one question drift, and the drift
would be a fare priced on one road beside a line drawn along another. And a simulated car that
cannot route logs at `warn` rather than `debug` — it still moves, so the failure is invisible in the
demo and reads as a problem with the map.
