---
id: B-57
title: "A pickup outside the graph is 422 on two routes and 500 on the one a rider uses"
status: done
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-57 — A pickup outside the graph is 422 on two routes and 500 on the one a rider uses

`POST /api/routes` and `POST /api/quotes` map the router's refusal to **422** with its message.
`POST /api/rides` with the same point answers **500**:

```
order saga … failed systemically: Point 0 is out of bounds: 0.0,0.0,
the bounds are: 14.221363,14.8270196,45.8671503,46.264117
```

One condition, two answers, and the worse one is on the route the product actually orders through.
`ServiceAreaStep` cannot help: it is `VALIDATION` and `QuoteStep` is `ENRICHMENT`, and petich runs
enrichment first — so the router throws before the step whose whole job is to refuse this politely
ever runs. Its own KDoc ("nothing before it has side effects, so a rejection is a refusal, not a
rollback") describes a position in the saga it does not occupy.

- **The area check has to happen before the road is priced, and that means before the saga.** The
  cheapest correct place is the route handler or `QuoteStep` itself: both ends of the journey are in
  the request, the check is two comparisons, and a refusal there is a 4xx with a sentence rather than
  a saga that fails systemically.
- **And the declared area should agree with the graph.** `ServiceArea.LJUBLJANA` is
  45.95–46.30 / 14.35–14.70 while the extract's bounds are 45.867–46.264 / 14.221–14.827: a point can
  be inside the area and outside the graph, and one inside the graph and outside the area is priced
  and then cancelled with no reason (see [B-58](B-58-the-rejection-nobody-writes.md)). Whichever is
  authoritative, one of them should be derived from the other rather than typed twice.
- The rejected alternative is mapping the router's exception to 400 in `StatusPages` and leaving the
  ordering alone: it stops the 500 and still prices a journey nobody can take.
- Deliberately **not** covered: what the client draws for it. R5·a is the kit's "no cars nearby"; an
  address outside the city is a different sentence and belongs with R3.

- AC: ordering with a pickup the router cannot reach answers 4xx with a sentence about the service
  area, and the saga is not started.
- AC: the service area and the graph's bounds come from one place, or the document says which wins.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSteps.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/RideRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/route/`

## What it turned out to be

**The check moved in front of the saga, and the area moved into the graph.**

`RequestRideUseCase` refuses before a `Petich` row exists, throwing `OutsideServiceAreaException`,
which `StatusPages` maps to the same **422** the two other routes give — deliberately the same,
because a pickup outside the city and a pickup the router cannot snap are one condition for a rider.
`ServiceAreaStep` stays where it is: a saga resumed from a row has to validate again, and this check
is about not starting one.

**`RouteEstimator.servedArea` is now the single answer**, read from `hopper.baseGraph.bounds`. The
constant beside the step said 45.95…46.30 / 14.35…14.70 and the extract spans 45.867…46.264 /
14.221…14.827 — a point could be inside either and outside the other, and both mistakes were live:
a journey priced and then cancelled with no reason, and a router that threw where the step said the
area was fine. `ServiceArea` moved next to the port, because it is a property of the roads.
`StraightLineRouteEstimator` keeps `LJUBLJANA` and says why: it can price a line between any two
points on earth, so its served area is a decision rather than a fact.

**And the graph guard's control stopped borrowing production code.** `KoinGraphTest`'s trap used
`RequestRideUseCase` to demonstrate that `verify()` skips a defaulted lambda; its constructor has now
changed twice underneath it — a clock in B-45, the served area here — and each time the control had
to be repaired before it could report anything. It has a two-line class of its own now. A control
that depends on production code keeping a shape is a control that reports on that shape.

**Verified through the route**, with the control: two tests post a ride with each end in turn at
null island, assert 422 and the word that says which end is the problem, and assert no ride reached
the rider's own list. Removing the check makes both fail.
