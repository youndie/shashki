---
id: B-92
title: "A receipt goes out again every time the settlement's last pass is retried"
status: done
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

## Findings

**The claim, taken before the mail and arbitrated by a primary key.** `receipt_claims` is keyed by
`ctx.idempotencyKey`, and `ExposedReceiptClaims.claim` is an `insertIgnore` whose row count is the
answer — not a read followed by a write, which is two statements and a race: two instances re-running
one settlement would both read nothing and both send. The same shape `payouts` already uses to make a
settlement that ran twice collide instead of paying twice.

**At most once, which is the trade this product had already made.** `SendReceiptUseCase` swallows a
send failure — "a settlement that rolled back because a mail server was down would be the tail
wagging the dog" — and `Settled.RECEIPT` exists so a ride whose receipt never went can be found. A
missing receipt is something this system notices and lives with; a duplicate is not. No new product
decision was needed, which is why this was not a `question`.

**`Settled.RECEIPT` keeps its meaning.** A member that finds the claim gone enriches `true` rather
than `false`: the first attempt made the receipt go, and saying "it never went" would be a different
fact and the one that channel is read for.

**The window is named and the instruction is in the migration.** A row per settlement kept for ever
is a table that only grows, so `receipt_claims_claimed_at` exists for a prune to read and the
migration says what it would run. No job does it — this is a demo database — and saying that is
better than shipping a sweeper nobody asked for.

## The first version of the test was green for the wrong reason

It ran the saga through the engine twice. **That passes whether or not the claim works**, because
petich short-circuits a terminal saga and never reaches the member — so it asserted nothing about
what it claimed to assert. The mutation is what said so: removing the claim check left it green.

It calls the member directly now, through `PetichMemberProbe` — petich's own context, so the member
is asked exactly what the engine asks it. Re-mutated after the rewrite: the case fails.

**This is the session's own lesson landing on me**: a test that passes for a reason other than the
one it names is the failure the mutation step exists for, and it was caught only because the step is
not optional.

## And a third table the hand-written truncate list did not know

`PostgresHarness.truncateAll` names its tables in one string. Its comment already records two
occasions where that bit — a payout left behind, then a rating — and this made three: a claim from an
earlier test silenced the next one's receipt, and the failure arrived as a send that never happened
rather than as the fixture that prevented it. Added, with the pattern written down beside it. A guard
that derived the list would be better and is out of this item's scope.

**Verification.** `:server:build --rerun-tasks` on the Linux box, exit code read rather than piped.
`SchemaTest` covers the new table and its vacuity guard went from four to five; the index is declared
on the Exposed table as well as in the migration, because an index that lives only in the database is
a rule the application cannot see — which `SchemaTest` caught on the first run.
