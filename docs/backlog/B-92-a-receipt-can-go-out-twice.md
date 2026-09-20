---
id: B-92
title: "A receipt goes out again every time the settlement's last pass is retried"
status: open
priority: P1
size: M
stage: stage-6-what-running-it-said
---

# B-92 — A receipt goes out again every time the settlement's last pass is retried

`PublishSettled` sends the receipt through `SendReceiptUseCase` and **then** calls `ctx.emit`. petich
advances a saga's position only after a member's body returns, so anything that makes that pass run
again makes the mail go out again.

That is not only the crash window it looks like. petich's `processWithRetry` re-runs the **whole
pass** on an optimistic-lock conflict, `maxProcessAttempts` times by default five — so two requests
touching one settlement is enough. petich's own `AnnouncementRunsAgainTest` measures six sends for
one saga under exactly that.

konekt is not exposed: its announcements are `ctx.emit` and nothing else, and the outbox key absorbs a
repeat. Ours is the only announcement in the portfolio that does I/O.

## What the fix cannot be

- **`ctx.idempotencyKey` alone.** petich B-50 made it the rule for a remote effect, and it works when
  the far side deduplicates. **SMTP does not.** `SmtpReceiptSender` opens a session and hands a
  message to a relay; there is no caller-chosen name a mail server will collapse two sends under.
- **`Settled.RECEIPT` in the enriched payload.** It is written by the same pass that did not commit —
  the exact shape of petich B-43, where the evidence is missing precisely when it is needed.

## What it can be, and this repository has already decided the trade

Two forms, and they differ in which failure they choose:

1. **Claim before sending** — a durable "this key was sent" record written *before* the mail, keyed by
   `ctx.idempotencyKey`. A crash between the claim and the send loses that receipt; a retry never
   duplicates one. **At most once.**
2. **Move the send behind the outbox** — emit the event in the saga and let a relay send the mail,
   deduplicating on the outbox key. **At least once**, delivered even if this process dies, at the
   cost of taking the mail out of the saga.

**`SendReceiptUseCase` has already said which failure this product accepts**: it swallows a send
failure on the grounds that "a settlement that rolled back because a mail server was down would be
the tail wagging the dog", and `Settled.RECEIPT` exists so a ride whose receipt never went can be
found afterwards. A missing receipt is a case this repository is already built to notice and live
with. A duplicate is not.

That points at (1) without needing a new product decision — but it needs a table, so it is a schema
change and not a migration, which is why it is here rather than folded into petich B-50.

## Acceptance

- A settlement whose last pass runs more than once sends **one** receipt, under both causes: a
  process that died inside the announcement, and an optimistic-lock retry on a healthy instance.
- The claim is keyed by `ctx.idempotencyKey`, so it is the same string on every attempt without the
  member inventing one.
- The window the claim lives in is stated. A row per settlement kept for ever is a table that only
  grows; say what removes it.
- `Settled.RECEIPT` keeps meaning what it means — the receipt went, or it did not — and does not
  quietly become "the claim was taken".
- The test uses a sender that counts sends. Asserting on the claim table instead would pass while the
  relay still saw two messages.
