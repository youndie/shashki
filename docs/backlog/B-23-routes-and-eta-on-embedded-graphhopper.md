---
id: B-23
title: "Routes and ETA through GraphHopper embedded in the server"
status: open
priority: P1
size: M
stage: stage-2-saga
blocked_by: [B-06]
---

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

- AC: `POST /routes` with two points inside the city returns a polyline, metres and seconds; the
  same request against a cached graph answers in under 50 ms on the Linux box.
- AC: the ENRICHMENT step of the order saga takes its distance and duration from this endpoint, and
  a class tile's ETA matches the route's duration for the nearest candidate.
- AC: the simulator ([B-20](B-20-matching-geo-index-and-driver-simulator.md)) moves its cars along
  routes from this endpoint, not along straight lines.
- Anchors: `server/build.gradle.kts`, `server/src/main/kotlin/io/github/youndie/shashki/server/`
