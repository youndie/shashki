---
id: B-42
title: "A driver who finishes a ride is reserved for ever, and the rider is still shown their wait"
status: open
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
- **The order button is dead after a refused ride.** `ClassPickerViewModel.order()` returns early
  while `ordering` is true; on the path where the ride fails and the trip screen pops back, the flag
  is left set, and the picker's only action does nothing for the rest of the session.
