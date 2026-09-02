---
id: B-64
title: "The offer reaches the driver's client and never reaches the driver's screen"
status: done
priority: P0
size: M
stage: stage-6-what-running-it-said
---

# B-64 — The offer reaches the driver's client and never reaches the driver's screen

With [B-53](B-53-the-driver-bundle-cannot-go-online.md) fixed, a signed-in driver is in the index and
the server offers them the ride. The client's own poll gets it: the server log shows
`200 OK: GET /api/driver/offers/rider@example.com` every two seconds for the full fifteen seconds an
offer lives, then 404 when it expires. **The card is never drawn.** The screen stays on *waiting*,
the offer times out, the cascade runs out of drivers and the ride is cancelled — from the rider's
side this looks exactly like a city with no cars in it.

What was observed, so the next person does not re-derive it:

* the body on the wire matches `OfferView` field for field, so it is not a serialisation failure —
  and one would be invisible anyway, which is a defect of its own (see below);
* nothing is thrown: the browser console holds only the poll's ordinary 404s;
* the shift's own count stopped rising while the poll kept answering, so the socket's flow and the
  poll's flow are in different states — one stalled and one did not.

- **The suspicion to check first is that a failure is being reported as an absence.**
  `WatchOfferUseCase` emits `runCatching { offers.forDriver(driverId) }.getOrNull()`, and
  `ShiftViewModel.onOffer(null)` means *the board is empty*. Anything that throws inside the
  repository — a body that will not parse, a 401 on a token that expired mid-shift, a transport
  error — is therefore indistinguishable from "no offer", for ever, silently. Whatever the cause of
  this particular bug turns out to be, that conflation is worth removing on its own: a poll that
  failed and a board that is empty are different facts and the screen can say so.
- **The second suspicion is the countdown.** `onOffer` computes `remainingAtReceipt()` and, if it is
  zero, the countdown coroutine calls `clearOffer()` immediately — the card would appear for one
  frame and vanish. The two clocks are the server's own pair, so this should not happen; it is cheap
  to assert and cheap to rule out.
- Deliberately **not** covered: making the offer arrive over the socket instead of the poll.
  `endpoint-driver` records why the board is polled, and that reasoning has not changed.

- AC: a driver who is online and is offered a ride sees the offer card, and the golden of that state
  is the one that already exists.
- AC: a poll that fails is not drawn as an empty board — the screen says something, and a test
  distinguishes the two.
- AC: whatever the mechanism turns out to be, the item says which of the two suspicions above it was,
  or that it was neither.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/offer/domain/UseCases.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/ui/ShiftViewModel.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/offer/data/HttpOfferRepository.kt`

## What it turned out to be

**The card is drawn, and the honest answer to "which of the two" is: both were real, and neither can
be shown to be the one.** The AC asked for that answer and this is it. Two defects were found, each
reproduced by a test that fails against the old code:

* **A poll that failed was drawn as an empty board.** `runCatching { … }.getOrNull()` turned a body
  that would not parse, a token that expired mid-shift and a dead network into *no offer*, silently
  and for as long as they lasted. `WatchOfferUseCase` now emits a named outcome — `Board.Empty`,
  `Board.Offered`, `Board.Unreachable` — the card that is up stays up when a poll cannot be made, and
  the shift screen says `offers unavailable` beside the count. It also uses `suspendRunCatching`,
  because the ordinary one swallows cancellation and going offline looked like a failed poll.
* **An offer arriving with no seconds left was drawn for one frame and then suppressed for ever.**
  `clearOffer` files the ride as finished so a poll already in flight cannot put the card straight
  back; started at zero it ran in the same frame, so every later poll carrying that ride was ignored
  while the server was still waiting for an answer. An offer with nothing left is now drawn and not
  counted down — whether an answer was in time is settled where the saga is, and the board going
  empty is what takes the card down.

**What removed the symptom is not established.** The stand's offer carried fourteen seconds, so the
zero-seconds path was not what happened there; the failure-as-absence path could have been and was
never caught in the act. A third change landed between the two observations —
[B-54](B-54-the-shift-counter-counts-frames-nobody-took.md) split the socket into a sender and a
reader — and the frozen count that accompanied the symptom is exactly what a stalled sender looks
like. Three candidates, one disappearance, and saying which would be a guess.

**Verified where it failed.** On the stand: signed in, online, ordered — and D3 appears with
`$ 6.48`, the timer at 11, the bar draining, accept and decline. The count beside *waiting* reads
`11 positions taken`, which is B-54's number and the first time this screen has said something the
server agrees with.
