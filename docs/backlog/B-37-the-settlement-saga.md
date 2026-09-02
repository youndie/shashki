---
id: B-37
title: "The settlement saga, whose parts are all written and none of them called"
status: done
priority: P0
size: L
stage: stage-2-saga
---

# B-37 — The settlement saga, whose parts are all written and none of them called

Research §1.4c says one ride is **two** sagas and a stretch of no saga: the order saga runs
`REQUESTED → ASSIGNED`, the trip is the driver's own transitions with nothing to compensate, and
`COMPLETED` opens the settlement — capture, payout, receipt, events. The first one is built and
killed at every phase boundary by its own tests. The second does not exist.

**P0, and the priority is the point of the item.** Stage 2 is called "the order survives the process
dying" and the goal at the top of the backlog names what a stranger judges this product by: the
driver was assigned, the process died, and *the card was not left holding money*. Half of that
sentence is demonstrated. The other half cannot be, because nothing ever completes a ride — so every
completed ride would leave a hold for ever, if one could complete.

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

## What it turned out to be

**The saga was the easy half. What was missing was everything that reaches it.**

`PaymentGateway.capture` had been implemented since B-11 and called by nobody. `SendReceiptUseCase`
was written, tested against a real SMTP server in B-14, and constructed in no DI module. There was no
way to move a ride past `ASSIGNED` at all, so nothing could ever have called either. Three pieces at
one end of a mechanism, joined at neither — the same shape as B-32's kompot finding and B-29's 409.

**The trip needed a table, not a saga**, which §1.4c had said in words and nobody had had to act on.
Four states, driver-driven, nothing to compensate. The row appears on the driver's *first* transition
rather than when the order saga completes: creating it inside a saga step would be a side effect with
no compensation, and the absence of a row is a perfectly good way of saying "assigned, not started".
The ride a rider reads is the saga's row overlaid by the trip's, and only while the saga says
`ASSIGNED` — a cancelled saga stays cancelled whatever a stale trip row claims.

**One engine runs both sagas.** `supports(payload)` is what petich filters the interceptor list by,
so the settlement's five steps live beside the order's in one list. `orderSagaEngine` became
`sagaEngine`, because the old name would have been a lie the moment the second saga existed.

**A capture needs an amount, and the fee test is what said so.** The first version captured the whole
hold — correct for a fare, and for a cancellation it charges a rider the entire journey for a car
they sent away. The test expected a quarter and got the lot. That is the whole of the difference
between the two settlements: five identical phases and one number, and the number was the one thing
not being carried. `PaymentGateway.capture(hold, amountCents)` now, with a partial capture like a
real provider's.

**And the compensation of a capture is a refund, not a release.** The gateway grew a fourth method
rather than reusing the third: releasing lets go of money nobody took, refunding gives back money
that moved, and in a real provider they are a different call, a different fee and a different line on
somebody's statement. Saying so in a mock is the point of having one.

**"Captured exactly once" lives in two places and both are asserted.** petich does not re-run a step
it has already committed, so a process that dies after AUTHORIZATION resumes at EXECUTION — the
abandoned-settlement test reconstructs exactly that row and requires the money not to move again. And
underneath it the gateway refuses a second capture of a hold that is gone, which is the difference
between a bug that is found and a bug that is a bank statement.

**The two cancellations diverge, which is most of why this product exists.** Before a driver is
assigned, cancelling compensates the order saga: the hold released, nobody charged. After, the order
saga is finished and cannot be rolled back, so what runs is this saga with a fee — a quarter of the
fare, captured, a fifth of that to the driver. Same word, two mechanisms, one test that shows both
numbers.

**A fixture bug the new tables exposed.** `PostgresHarness.truncateAll` cleared two tables of four,
so a payout left by one test made the next fail on a primary key — reported as a *systemic saga
failure*, which is a message about petich for a fact about the fixture.

**The driver's screen has a button now**, and B-29's note about why it did not is answered rather
than left. One action, not four: a trip is a sequence and the driver is at one point in it. The order
moved to `:protocol` as `TripProgression` — the server refuses a transition that is not next and the
client has to know which button to draw, and two copies of that list is a client offering a button
the server refuses. The screen takes its state from the answer and never from the intention: the last
press is the one that takes the rider's money.

**Not covered, and named:** the receipt goes to the address on the token, so a stand with no provider
sends none and records that it sent none. `ReceiptConfig` is the switch, `UnsentReceipts` is the
honest absence, and the route-level test asserts exactly that — the settlement completes and writes
`receipt.sent = false`. Sending it for real is `SettlementSagaTest`, where a payload with an address
can be built directly, and against a real relay it is still B-14's gated test.

Nineteen new tests: seven through the routes, six against the saga and a real Postgres, three on the
driver's view model, plus the schema and the graph.
