---
id: B-42
title: "A driver who finishes a ride is reserved for ever, and the rider is still shown their wait"
status: done
priority: P0
size: S
stage: stage-2-saga
---

# B-42 — A driver who finishes a ride is reserved for ever, and the rider is still shown their wait

**Found by running the stand, not by a test** (2026-09-02): the two desktop clients against the
compose stand, one driver online, one ride ordered from the rider's window. The ride went through —
offer, acceptance, trip, settlement, a payout of $23.16 against a fare of $28.96. The **next** order,
a minute later with the same driver waiting, was cancelled the instant it was made.

`reserve` and `release` occur in exactly two files, both inside the order saga
(`OrderSteps.kt:213,256,332,351`). `OfferStep` reserves a candidate before offering; every path that
loses the driver releases — declined, ignored, cancelled, compensated. The path that **keeps** the
driver is `DriverAnswer.Outcome.ACCEPT`, which calls `withdrawKeepingReservation` on purpose: the
reservation is what stops a second ride from being offered to a driver who is already carrying one.

Nothing releases it when the ride ends. `AdvanceTripUseCase` moves the trip to `COMPLETED`, the
settlement saga charges and pays out, and neither has ever heard of `DriverReservations`. So a
driver's second ride does not exist: `candidates.firstOrNull { reservations.reserve(…) }` fails for
them for the lifetime of the process, and the saga answers `no cars nearby`.

**What makes it worse than a stuck flag is that the rider is not told.** `PickupEta` asks the same
index and does not consult reservations — correctly, because a wait is about where cars are — so the
class tile keeps showing `0 min` for a car that can never be matched. The screen says a car is
around the corner and every order is refused. That is the same shape as this repository's other
findings: **two ends of one fact, joined nowhere** — the ETA and the dispatch disagree about what
"available" means, and nothing compares them.

Measured on the stand, in order:

| Step | What happened |
|---|---|
| server restarted, driver online, order | ride `6cfc197e` COMPLETED, trip COMPLETED, settlement COMPLETED, payout 2 316 ¢ |
| same driver still online, order again | `CANCELLED` in the POST's own response; `/api/driver/offers/driver-1` → 404 for ever after |
| `/api/quotes` throughout | `ECONOMY pickupEtaSeconds: 0` — the car the rider cannot have |

- AC: a driver completes a ride and is matched to a second one. The test drives two rides through
  one driver against a real Postgres, as `OrderSagaTest` already drives one.
- AC: the release happens where the ride ends rather than in a `finally` somewhere — a trip that
  ends by cancellation after acceptance frees the driver too, and that path gets its own case.
- AC: something compares the two answers. A rider shown a wait for a driver the dispatch would
  refuse is the defect; a test that asserts "the class the ETA offers is a class the saga can serve"
  is what would have caught this without a stand.
- AC: the driver's own screen agrees. After the ride the app returns to `waiting`, which is honest
  today only by accident — it is waiting for an offer that cannot arrive.

Two smaller things the same session turned up; they belong here rather than in their own items until
somebody looks at them:

- **A trip watcher outlives its screen.** After the trip screen was popped the rider kept polling
  `/api/rides/{id}/driver` every three seconds for twenty-five minutes, for a ride that had been
  cancelled. `WatchDriverUseCase`'s loop is not on the view model's scope, or is not cancelled with
  it.
- ~~The order button is dead after a refused ride.~~ **The guess was wrong and the real thing is
  worse.** `ordering` is lowered on the next line and a test that "proved" the fix passed without it
  — vacuous, and dropped rather than shipped. What was actually seen is that the picker **never
  reloads when it is returned to**: the quotes it holds are the ones it loaded when the application
  started, so after a failed ride the screen keeps saying "no cars nearby" — correctly disabling the
  order bar — for a driver who has been free for minutes. The lifecycle of that wait is
  [B-43](B-43-the-rider-sees-the-wait-and-its-end.md)'s subject, and it inherits this.

## What it turned out to be

**One rule with no owner, and a second answer to the same question.**

The reservation is the rule: `OfferStep` takes it before offering and keeps it when the driver
accepts — deliberately, because that is what stops a second offer reaching a driver who is already
carrying somebody. Every path that loses the driver gives it back. The path that *finishes* the ride
gave nothing back, because a ride finishes in `AdvanceTripUseCase` and in `CancelRideUseCase`, and
neither of them had ever heard of `DriverReservations`. The word for that here is that the rule had
four owners on the losing paths and none on the winning one.

`releaseFor(rideId)` is what both call now — **by ride and not by driver**, so the caller cannot
release the wrong one, and it sits before the settlement rather than after it: money can fail and be
retried, a driver held by a failed capture cannot.

**The second answer was the one that made it invisible.** "Available" was computed twice, out of two
different facts: `PickupEta` asked the index, which knows geography; the saga asked the index and
then reserved, which is geography plus who is busy. So the tile went on showing `0 min` for a car no
order could get — and the two were never compared anywhere. `FreeCandidates` wraps the index once and
both read it, which is the structural half of the fix; the assertion is
`a wait is only shown for a class the dispatch can actually serve`, with a positive control first so
that a server which never names a wait cannot pass it.

**Every one of the three tests was checked against the unfixed code** — the second ride, the
cancellation, and the wait all go red without their line, and the run is in this session's log. The
`clean` in `SettlementTest` gave them a Postgres each; nothing else was needed, because the thing
that was missing was never a mechanism.

**AC4 needed no code.** The driver's screen says `waiting` after a ride, which was honest only by
accident while no offer could ever arrive; it is honest on purpose now. The client's own leak — a
trip watcher that outlived its screen by twenty-five minutes — is still open above and wants a
Navigation 3 answer rather than a guess.
