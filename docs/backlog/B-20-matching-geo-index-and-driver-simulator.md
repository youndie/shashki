---
id: B-20
title: "Matching: the geo-index, the candidate query and the driver simulator"
status: open
priority: P0
size: L
stage: stage-2-saga
---

# B-20 — Matching: the geo-index, the candidate query and the driver simulator

The saga's EXECUTION step offers a ride to a driver, and [B-12](B-12-offer-as-a-suspended-saga.md)
cascades that offer across several. Neither has anything to offer *to*: nothing in the backlog
produces candidates. Research §1.4d names the three pieces — an in-memory geo-index of online drivers
fed by their WebSocket position stream, a candidate query by class and distance sorted by distance
and rating, and a simulator that keeps virtual cars moving on the real road graph so the demo has
drivers without having people. The brief had all three; the first pass of the backlog had none.

- **In memory, on the server, and not through the broker.** Research §1.6a: driver coordinates go
  straight from the WebSocket into the index and never enter a booblik topic. The index is
  geohash-bucketed and rebuilt from the stream on restart — it is a cache of the last known
  positions, not a record, and losing it costs one minute of positions.
- **The simulator is a client of the same WebSocket, not a back door into the index.** It drives N
  virtual drivers along GraphHopper routes ([B-23](B-23-routes-and-eta-on-embedded-graphhopper.md))
  and answers offers with a configurable accept / decline / ignore mix, because B-12's acceptance —
  three consecutive declines, a deadline nobody answers — needs drivers that misbehave on purpose.
- The rejected alternative is a stub list of candidates inside the saga. It is how B-11 gets built
  and tested before this lands, and it is the wrong thing to demo: a cascade over a constant list
  proves the saga and nothing about matching.
- Not covered: surge and zone pricing. The candidate query returns distance and ETA; what they cost
  is ENRICHMENT's business.

- AC: with the simulator running 20 drivers, a ride request returns candidates sorted by distance
  then rating, and the nearest online driver of the requested class is first.
- AC: killing and restarting the server rebuilds the index from the position stream within one
  reporting interval, with no candidate served from a stale process.
- AC: B-12's cascade and deadline acceptance run against simulated drivers set to decline, with no
  hand-written candidate list left in the saga.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Ride.kt`
