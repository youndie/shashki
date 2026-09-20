---
id: B-91
title: "A hold whose answer is lost is never released, because the id it would be released by came back in that answer"
status: wip
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

**petich B-48 says which form this port needs, and it is not the simple one.** `hold()` returns a
generated `HoldId` and `release()` takes that id — the caller's name buys a replay, not a handle — so
"cancel whatever is under this key" has nothing to address here. The form for a far side that only
deduplicates is **replay then cancel by the id the replay returns**:

```kotlin
override suspend fun compensate(ctx: PetichStepContext, payload: OrderPayload) {
    val hold = payments.hold(ctx.idempotencyKey, payload.paymentMethodId, quote.amountCents, …)
    payments.release(hold.id)
}
```

If the first call landed, the replay is answered by it and no second hold is taken; if it never
landed, the replay creates one and the release removes it. Net zero either way, which is exactly what
the missing record could not tell us. It costs one extra gateway call on the rollback path.

That form has a precondition this repository owns rather than petich: **the gateway must remember an
idempotency key for longer than `maxCompensationAttempts × stuckAfter`.** Past that window the replay
is not a replay — it is a second hold, released, with the first still standing. petich's
`ReplayThenCancelTest` keeps that failure as a live case; whatever we do here has to state which
window our gateway actually gives us.

petich B-43 named this class of defect and added `ctx.idempotencyKey` — a deterministic
`"<saga id>:<member key>"`, the same string on the forward pass and in the compensation — so a
rollback can say "cancel whatever is under this name" and be a no-op when there is nothing. **konekt
already does this by hand**: its `HoldFunds` passes `ctx.petich.id` into `balances.hold(...)`. Our
`PaymentGateway.hold()` takes no such argument and `release()` takes the generated `HoldId`, so the
port cannot express it yet.

## Acceptance

- `PaymentGateway` can be told what to call an operation. Whether it also gains "release by that
  name" or keeps generated-id cancellation and takes the replay form is decided against what the real
  gateway offers, not against what is convenient — petich B-48 has both shapes written out.
- If the replay form is chosen, the gateway's actual key-retention window is named in the item's
  findings and checked against `maxCompensationAttempts × stuckAfter` for our configuration.
- `HoldPaymentStep` and `CaptureStep` name their effect before the call and undo by that name. The id
  stays in the enriched payload — that channel has a second reader and is not what is wrong.
- A test where the gateway **commits and then loses the answer**, and the fare is released anyway. A
  test where the call never landed does not exercise this: there the id is absent *and* the hold is
  absent, so a rollback that does nothing is right by accident.
- The fake gateway used by the suites grows the same behaviour, or the test above cannot exist.
