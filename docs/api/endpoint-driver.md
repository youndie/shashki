---
id: endpoint-driver
title: The driver's surface — going online, offers, and moving the trip along
type: api_endpoints
status: active
services:
  - shashki-server
contract_source:
  - shashki:protocol DriverOffers, DriverRides, DRIVER_POSITIONS_PATH
  - shashki:protocol DriverReport, OfferView, OfferAnswer, TripAdvance
parent_feature: feature-the-trip
---

# API: the driver

## Routes — all of them, no exceptions

| Method and path | Auth tier | Purpose |
|---|---|---|
| `WS /api/driver/positions` | **driver ticket** | the driver's position, a few times a minute, straight into the geo-index |
| `POST /api/driver/ticket` | driver token | one short-lived ticket, for the socket above |
| `GET /api/driver/offers/{driverId}` | driver token | the offer waiting for this driver, or 404 |
| `POST /api/driver/offers/{rideId}/answer` | driver token | accept or decline |
| `POST /api/driver/rides/{rideId}/advance` | driver token | move the trip to the next state |

**The hole is shut** (B-52). It said "public, temporarily" here for two stages, and what it meant was
that anybody who knew a driver's id could read the offer waiting for them, accept it, and advance the
trip to `COMPLETED` — which captures the rider's hold.

**The identity is the token's subject, and it replaces rather than compares.** The `{driverId}` in
the offers path and the `driverId` in the two bodies are ignored the moment there is a principal;
they survive because a server with no provider configured is a running configuration — the demo — and
there they are the only source there is. A route that took an id the caller chose *and* a token would
have to compare them, and a route that has to compare them will one day not.

**The socket is the exception, in both directions.** A browser cannot put a header on a WebSocket, so
the upgrade carries a one-shot ticket minted at `POST /api/driver/ticket` behind the ordinary token
check — thirty seconds, single use, and worth nothing in an access log. And a frame is *compared*
rather than replaced: a position for anybody but the connected driver is dropped and counted, because
relabelling it would file somebody else's car under this driver.

What has not moved: the class and the rating are still self-reported, because there is no driver
record to read them from. The second lock on the last route is still there and is still worth having
— `AdvanceTripUseCase` compares the identity with the driver the *order saga* assigned, so a
signed-in driver cannot drive somebody else's trip.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| the position socket | `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/DriverPositionRouting.kt` |
| offers and answers | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/DriverRouting.kt` |
| advance | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/trip/TripRouting.kt` |

## The socket is a socket and the board is polled

A driver's shift **is** the connection being up: closing it is going offline, and a crashed client is
covered by staleness in the index instead. The offer, by contrast, is one message in a shift — pushing
it would mean a second connection and a second reconnect policy for something that arrives once an
hour, so the board is polled. Research §1.6a is why positions never enter the broker at all.

## The offer carries both ends of its deadline

`OfferView` has `expiresAtEpochMs` **and** `nowEpochMs`, so a client counts a duration it was handed
rather than subtracting its own wall clock from a deadline it did not set. A laptop an hour out would
otherwise draw fifteen seconds that never start.

## One route with a target rather than four verbs

`POST .../advance` takes the state the driver says the trip has reached, and only the next one is
accepted: `ASSIGNED → ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED`. Four verbs would spread that rule
across four handlers. The order lives on the wire — `TripProgression` in `:protocol` — because the
server refuses and the client has to know which button to draw, and two copies of that list is a
client offering a button the server refuses.

## Errors

| Condition | Status | Body |
|---|---|---|
| no offer for this driver | `404` | `{"error": "no offer for driver <id>"}` |
| **accepting an offer that has moved on** | `409` | `{"error": "offer for ride <id> is no longer <driver>'s"}` |
| a transition that is not the next one | `409` | `{"error": "a trip goes <from> → <expected>, not <from> → <to>"}` |
| advancing somebody else's ride | `404` | confirming that it exists is itself an answer |

**The 409 on accept is the one worth reading.** `DriverAnswerStep` already refused a stale answer by
resuspending for the driver who *is* offered — correctly, and completely silently: the route answered
`200 OK` with the ride unchanged, so a driver whose tab had been asleep would have been shown somebody
else's trip. It took a second client to notice ([B-29](../backlog/B-29-the-driver-bundle.md)).
