---
id: B-94
title: "A tap on the map cannot become a place, though the projection has always known how"
status: open
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-94 — A tap on the map cannot become a place, though the projection has always known how

`Projection.toGeo(offset)` is declared on the interface, implemented by **both** `MapViewport` and
`TileProjection`, correct in both (`MapViewportTest` round-trips it through `toCanvas`), and called by
nothing outside a test. The other direction, `toCanvas`, is called by every screen that draws a
marker.

That asymmetry is the whole finding. A map surface that can place a point on the canvas and cannot
turn a canvas point back into a place is a map you can look at and not one you can use: **no screen
in this product lets a rider pick a pickup or a dropoff by touching the map.** Addresses are typed,
or they come from the stub geocoder.

**Nothing here involves petich.** This is `shared-ui`'s map and a Compose screen; the saga engine
does not appear in it. Saying so because the numbers below invite the opposite reading: they are
**this repository's** items, and a reader who has been living in petich's backlog will read `B-41` and
`B-42` as the announcement type and the builder's name, which are different items in a different
repository that happen to share the numbers.

This is the shape *shashki* has now found five times — a mechanism written at both ends and joined at
neither: [B-32](B-32-which-screens-the-server-sends.md) (which screens the server sends),
[B-37](B-37-the-settlement-saga.md) (the settlement saga written and not called),
[B-41](B-41-the-rider-actually-signs-in.md) (the rider signing in),
[B-42](B-42-a-driver-is-reserved-for-life.md) (a driver reserved for ever), and this. Cited from
B-85, which sorted twenty-five findings of the same report and concluded that this is the one that is
not merely a test's window into a mechanism.

## Two ways out, and they are not the same size

- **Join it.** A tap on `MapSurface` becomes a `GeoPoint`, and the rider's address fields accept one.
  That is a screen change, a route that reverse-geocodes, and a decision about what the label says
  when the point is a field. It is the product answer and the larger one.
- **Withdraw it.** `toGeo` leaves the interface, and the two implementations lose a method nobody
  calls. That is honest and cheap, and it removes the ability rather than the promise — the day
  somebody wants the tap, they re-derive the arithmetic that is already written and tested here.

The second is not obviously wrong: an interface that promises what the product does not do is worse
than one that does not promise it. But the arithmetic is the hard part and it exists, so deleting it
costs more later than keeping it costs now.

## Acceptance

- One of the two is chosen, with the reason recorded here rather than in a commit message.
- If it is joined: a rider can set at least one of the two addresses by touching the map, and the
  screen's states say what happens when the tap lands on nothing a geocoder can name.
- If it is withdrawn: `toGeo` and both implementations go, `MapViewportTest`'s round-trip goes with
  them, and this file says what to read when somebody wants it back.
- Either way `./gradlew kapkanJoins` stops reporting `toGeo`, and B-85's tally is updated — the point
  of writing a number down is that the next run can be compared to it.
