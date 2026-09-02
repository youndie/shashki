---
id: B-29
title: "The driver bundle, which is the second one D10 chose"
status: open
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
