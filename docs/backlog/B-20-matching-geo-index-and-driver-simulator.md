---
id: B-20
title: "Matching: the geo-index, the candidate query and the driver simulator"
status: done
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

## What it turned out to be

Three pieces, and the saga did not change to take them: `GeoCandidateSource` implements the
`CandidateSource` port B-11 introduced, so the module binds the index where it used to bind a list
and the steps never learn the difference.

**The index is a grid, not a base-32 geohash, and the KDoc says why.** What a geohash buys is that a
query touches a bounded number of buckets instead of scanning every driver; a grid keyed by a pair of
integers buys exactly that in a fraction of the code. What the *string* additionally buys — a
sortable key a shared store can range-scan — is worth nothing while the index lives in one process's
memory, which is where research §1.6a puts it. The query walks rings outward and stops when the next
ring cannot beat the nearest hit already found. Staleness is 60 s, which is the item's own "losing it
costs one minute of positions" read as a number.

**The socket is the only way in.** `driverPositionRoutes` takes `DriverReport` frames straight into
the index; nothing about a position reaches booblik. A closed socket is a driver going offline; a
crashed one is covered by staleness, which is why the index needs no heartbeat of its own. A
malformed frame is logged and skipped rather than closing the socket — one client's bug should not
end its own stream.

**The simulator is a client of that socket and of the same HTTP API a driver's app uses**, per the
item's condition. Two loops per driver, not one: a report loop every few seconds and an offer-poll
loop several times a second. Folding them into one made every decline cost a reporting interval —
the simulator being slow, read as the server being slow.

- ~~AC: with the simulator running 20 drivers, a ride request returns candidates sorted by distance
  then rating, and the nearest online driver of the requested class is first.~~ Done, split in two
  because only half of it can be stated exactly: `DriverIndexTest` fixes drivers at known places and
  asserts the ordering rules; `MatchingTest` runs the twenty, waits for `onlineCount == 20`, plants
  one known driver fifty metres away over the same socket, and asserts that driver is first and the
  list is non-decreasing in distance.
- ~~AC: killing and restarting the server rebuilds the index from the position stream within one
  reporting interval, with no candidate served from a stale process.~~ Done — a second application
  instance starts with an empty index and fills from the stream. The stronger claim it rests on —
  that nothing is persisted — is structural rather than asserted: there is no repository and no
  migration for the index.
- ~~AC: B-12's cascade and deadline acceptance run against simulated drivers set to decline, with no
  hand-written candidate list left in the saga.~~ Done. `FixedCandidateSource` moved out of
  production into the test sources, so the module has nothing else to bind by accident;
  `SimulatedCascadeTest` runs three declining drivers end to end and the rider is told there are no
  cars.

**Three tests passed while proving nothing, and each is worth naming.** The cascade test first ran
against an *empty* index — no drivers, immediate compensation, `CANCELLED` with no reservations,
which is exactly what a real exhausted cascade looks like from outside. It now reads the saga's own
`offer.attempt` and requires 2: all three candidates asked in turn. Its second failure was real too —
the simulator assigns classes at random, so three drivers were one per class and a cascade over the
requested class was one offer long; the class is a policy now, like the behaviour. And `parkDriver`
in the route tests waited on a condition that was true before the driver existed, then closed the
socket it had just opened — which is precisely how a driver goes offline, so the ride was cancelled
for want of cars a line later.

**The formatter trap from B-12 bit a third time.** A scripted edit anchored on a test body ktlint had
reflowed matched nothing and said nothing, and the test kept failing for the original reason while I
looked for a new one. The rule that works: read the region, then edit against what is actually there.
