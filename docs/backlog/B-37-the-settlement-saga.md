---
id: B-37
title: "The settlement saga, whose parts are all written and none of them called"
status: open
priority: P1
size: L
stage: stage-2-saga
---

# B-37 — The settlement saga, whose parts are all written and none of them called

Research §1.4c says one ride is **two** sagas and a stretch of no saga: the order saga runs
`REQUESTED → ASSIGNED`, the trip is the driver's own transitions with nothing to compensate, and
`COMPLETED` opens the settlement — capture, payout, receipt, events. The first one is built and
killed at every phase boundary by its own tests. The second does not exist.

What makes it worth an item rather than a note is that its pieces are all here and none of them is
reachable:

| Piece | State |
|---|---|
| `PaymentGateway.capture` | implemented, and its own KDoc says it "belongs to the settlement saga… and is here so the contract is whole". Nothing calls it |
| `SendReceiptUseCase` and `SmtpReceiptSender` | written and tested against a real SMTP server (B-14). **Not bound in any DI module** — the receipt feature has no module, so nothing constructs them |
| the outbox | running, and its relay publishes to `LoggingPublisher` with a comment saying booblik is a later item |
| payout | does not exist in any form |

- **A hold that is never captured is the demo's most embarrassing possible state.** The order saga's
  acceptance is "no held payment and no reserved driver at any phase boundary" — true, and about the
  first saga only. Today every completed ride leaves a hold on the card for ever, because nothing
  ever completes a ride either.
- **The trip's transitions are the precondition and they are missing too.** There is no route to move
  a ride `ARRIVING → ARRIVED → IN_PROGRESS → COMPLETED`; the driver bundle draws the accepted ride
  with no buttons and says why ([B-29](B-29-the-driver-bundle.md)). Something has to produce
  `COMPLETED` before anything can be settled, so this item owns that half as well.
- **Cancellation after `ASSIGNED` is the same saga with a different amount.** §1.4c: before
  `ASSIGNED` it is the order saga compensating from the middle; after it, a trip ending early and a
  settlement that charges a fee. Same word, two mechanisms — and demonstrating that is most of why
  this product exists.
- **The events are the one place booblik finally earns its place.** §1.6a settled that positions do
  not go through a broker; what does is what happened to a *ride*, and a settlement is the ride's
  last word. Whether that lands in this item or the next is a decision this item makes rather than
  inherits.
- The rejected alternative is capturing the hold inline at `COMPLETED`. It is four lines and it puts
  a payment operation, a payout and an email in a request handler with no retry and no compensation
  — which is the thing this whole product is a demonstration against.
- Deliberately **not** covered: a real payment provider. `InMemoryPaymentGateway` is the brief's mock
  cashier and the integration contract is what is real.

- AC: a ride reaches `COMPLETED` through the driver's own transitions, and the settlement saga runs
  its phases against a hold that was actually taken by the order saga.
- AC: the hold is captured exactly once, and killing the process between phases does not capture it
  twice — the same test shape B-11 already runs, applied to the second saga.
- AC: no completed ride leaves an active hold, asserted through `PaymentGateway.activeHolds` the way
  B-11 asserts it for the first saga.
- AC: the receipt is sent by the saga rather than by nobody — `SendReceiptUseCase` is bound and
  called, and a mail server that is down does not roll the settlement back (its own KDoc already
  says which way that decision goes).
- AC: a ride cancelled after `ASSIGNED` settles a fee rather than compensating the order, and a test
  shows the two paths diverging at the same word.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/billing/PaymentGateway.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSaga.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/domain/SendReceiptUseCase.kt`
