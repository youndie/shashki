---
id: B-91
title: "A hold whose answer is lost is never released, because the id it would be released by came back in that answer"
status: open
priority: P1
size: M
stage: stage-6-what-running-it-said
---

# B-91 — A hold whose answer is lost is never released, because the id it would be released by came back in that answer

`HoldPaymentStep` takes the money and then writes the hold id into the enriched payload; its
compensation reads that id back and releases by it. Both halves are deliberate and both are right
about what they were written for — the id is read by `SettleRideUseCase` long after the saga ends,
which is what the carried payload is for.

What neither half covers is the ordinary failure of a distributed system:

- `payments.hold(...)` reaches the gateway and the gateway commits;
- the answer is lost; petich's phase timeout fires;
- `ctx.enrich(HOLD_ID)` never ran — it could not have, the id comes back *in* the lost answer;
- petich calls `compensate` anyway (it cannot tell a landed effect from a call that never landed);
- `ctx.enriched(HOLD_ID)` is null, the `?.let` does nothing;
- **the rider's fare is held and nothing will ever release it.** The saga ends FAILED, the ride reads
  CANCELLED, and the money is gone from the rider's available balance until a person notices.

`SettlementSteps.CaptureStep` has the same shape against `capture`.

petich B-43 named this class of defect and added `ctx.idempotencyKey` — a deterministic
`"<saga id>:<member key>"`, the same string on the forward pass and in the compensation — so a
rollback can say "cancel whatever is under this name" and be a no-op when there is nothing. **konekt
already does this by hand**: its `HoldFunds` passes `ctx.petich.id` into `balances.hold(...)`. Our
`PaymentGateway.hold()` takes no such argument and `release()` takes the generated `HoldId`, so the
port cannot express it yet.

## Acceptance

- `PaymentGateway` can be told what to call an operation, and can be asked to release or void by that
  name rather than only by the id it generated.
- `HoldPaymentStep` and `CaptureStep` name their effect before the call and undo by that name. The id
  stays in the enriched payload — that channel has a second reader and is not what is wrong.
- A test where the gateway **commits and then loses the answer**, and the fare is released anyway. A
  test where the call never landed does not exercise this: there the id is absent *and* the hold is
  absent, so a rollback that does nothing is right by accident.
- The fake gateway used by the suites grows the same behaviour, or the test above cannot exist.
