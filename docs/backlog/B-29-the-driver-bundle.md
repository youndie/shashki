---
id: B-29
title: "The driver bundle, which is the second one D10 chose"
status: done
priority: P1
size: L
stage: stage-3-surface
---

# B-29 — The driver bundle, which is the second one D10 chose

[D10](../research/research-architecture.md#d10-two-bundles-and-the-number-is-that-the-roles-are-5--of-one)
chose two bundles and [B-28](B-28-the-client-application-shell.md) built the first. This is the other
one, and most of what it needs already exists on the server: the offer is a suspended saga with a
deadline (B-12), the geo-index and the candidate query answer (B-20), and the driver's position
socket is what the simulator already speaks.

- **It is not a copy of the rider.** The rider polls a ride; a driver holds a socket open and pushes
  position. The offer screen has a countdown that expires whether or not anybody looks at it, which
  is a different failure mode from a request that can simply be retried.
- **The screens are drawn**: `OfferCard` and its countdown are components with goldens (B-04), and
  D3's action bar is transcribed. What is missing is the shell and the socket.
- **`installCrashReporting` and the address bar are solved problems now.** B-28 built both as ports,
  and this bundle binds the same ones — which is the test of whether they were ports or just the
  rider's own arrangement.
- The rejected alternative is one bundle with a role switch. D10 measured why: the runtime is 3.4 MB
  gzipped and identical for both, so a second bundle costs a person nothing and a shared one costs
  every person the other role's screens.
- Not covered: the driver's navigation view. B-23 says turn-by-turn is out of scope for the
  reference, and nothing since has changed that.

- AC: a driver bundle that goes online, holds its position socket open, receives an offer and can
  accept or decline it, against the server this repository already has.
- AC: the offer's countdown expires on the server's deadline rather than on the client's clock — a
  driver whose tab was asleep must not accept an offer that has already gone to somebody else.
- AC: the same `AddressBar` and `installCrashReporting` ports the rider binds, with no new
  implementation of either.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/OfferCard.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/DriverSimulator.kt`

## What it turned out to be

**The second bundle is mostly the first one's parts, and proving that was the item.** Two bundles are
only cheap if the second is assembled rather than written — D10 decided on that basis and it was an
intention until there was a second one. What `:driver` binds: the address bar, `installCrashReporting`,
the money and distance formatting, `OfferCard` and its countdown, `DriverTheme` — which had existed
since B-03 and had never been used. New code: the socket, and one function that prints a coordinate.

**Two of those moved rather than being copied, and the moving is the evidence.** `AddressBar` went
from `:rider` to `:shared-ui` unchanged, actuals and all; the formatting went with it, having written
its own argument in advance — "how a price ends up rendered differently in the two bundles" is what
its KDoc already said, and a copy per bundle is that failure with extra steps. A port with one binder
is an arrangement somebody has called a port.

**What is genuinely the driver's is the socket**, and it is the only place the two applications are a
different shape. The rider polls and each request fails on its own; a driver's shift *is* a
connection being up. So `ShiftRepository` is a flow whose lifetime is the socket's, and the screen
shows the count of positions the socket actually took — "waiting" is a word an application can print
over a dead connection, and a number that has stopped rising is not.

**The item found a defect on the server that the first bundle could not have found.** `DriverAnswerStep`
refuses an answer from a driver who is not the one being offered — correctly, by resuspending, and
silently: the route answered `200 OK` with the ride unchanged, so a driver whose tab had been asleep
would have been shown somebody else's trip. Nothing on the server was wrong, which is why nothing on
the server had found it; there was no client to make that call until there was one. It is now
`OfferGoneException` → 409, and the test was checked by removing the guard, which turns the 409 back
into the `200 OK`.

**The countdown had no clock it could trust**, which is the second criterion. `OfferView` carried only
`expiresAtEpochMs`, so a browser had to subtract its own wall clock from it — a laptop an hour out
draws fifteen seconds that never start. It now carries `nowEpochMs` beside it and the client counts a
duration it was handed; the test sets the server's clock four billion milliseconds away from anything
this process would call "now" and still expects fifteen. What the client's countdown does *not* do is
decide anything: reaching zero drops the card, and whether an answer was in time is settled where the
saga is.

**A defect of the screen's own, found by a test that was written wrong first.** When the countdown
reached zero the card came back two seconds later, because the board does not go empty the instant
the client's clock does — the withdrawal is the server's and the poll in flight still carries the old
answer. The screen now remembers the offer it has finished with until the board agrees.

Four goldens (`shift offline`, `shift waiting`, `shift with an offer`, `assigned ride`), recorded on
the mac and verified on Linux by the same `check` — B-02's claim holding for a third module. Fifteen
tests.

**Out of scope, and stated rather than stubbed.** The trip's own transitions —
`ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED` — have no route on the server, so the accepted-ride
screen shows what was taken and what the server says about it, and has no buttons. A control that
posted to an endpoint which does not exist would be worse than its absence. Turn-by-turn stays out
by B-23.

**[B-37](B-37-the-settlement-saga.md) built the routes and the screen has one button now** — the next
transition, and the last press is what captures the rider's fare. Turn-by-turn is still out.

**Not covered and not pretended:** there is no geolocation. The browser's API needs a permission
prompt and a device that is going somewhere; a fabricated drift would be the client inventing data
the server indexes as fact. The bundle sends its configured point, which is enough to be a candidate,
and movement stays `DriverSimulator`'s — which says what it is.
