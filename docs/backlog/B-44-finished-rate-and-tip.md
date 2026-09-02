---
id: B-44
title: "Finished: rate the driver and tip, and the tip is a second charge"
status: done
priority: P1
size: M
stage: stage-5-the-rest-of-the-kit
---

# B-44 — Finished: rate the driver and tip, and the tip is a second charge

The kit's R8 is the end of every ride — the sum, a rating, a tip — and today `COMPLETED` is the last
thing the rider's trip screen shows before it stops polling. Nothing stores a rating, so the
candidate query's "sorted by distance then rating" (B-20) sorts on a number every driver has at its
default. And a tip has nowhere to go: the hold the order saga took is the quote, and a tip is money
on top of it.

- **A tip is a charge, not a larger capture.** `PaymentGateway.capture(hold, amountCents)` cannot
  exceed the hold, and a real provider's cannot either. The gateway grows `charge(customer,
  amountCents)` — a new authorisation and capture in one, no hold, its own refund — and the
  settlement saga does not change: a tip arrives *after* settlement, by definition, so it is a step
  of its own with its own compensation rather than a sixth phase of a saga that already finished.
- **The rating is the driver's, the tip is the driver's, and neither reaches them yet.** Payout rows
  exist (B-37); the tip adds one. The rating becomes the number B-20's sort reads, which is the first
  time that sort key is something other than a constant — and `DriverIndexTest`'s ordering rules get
  a driver whose rating is worse than a nearer one's.
- **Rating without a tip is the common case and the screen says so** — the tip row is optional and
  *skip* is a first-class button, not a small link.
- The rejected alternative is folding the tip into the settlement's capture by raising the hold at
  quote time. It charges every rider for a tip they may not give, which is the kind of thing that
  gets a product a headline.
- Deliberately **not** covered: the driver rating the rider (D5's "rate passenger"). Same shape,
  other direction, and it waits for a reason to exist beyond symmetry.

- AC: `POST /api/rides/{id}/rating` and `POST /api/rides/{id}/tip`, rider token, each refused before
  `COMPLETED`; a tip is a `charge` on the gateway with a payout row and a refund path, and a process
  killed between the charge and the payout leaves neither half alone — the B-11 test shape, again.
- AC: `rider_finished` golden against R8 in both themes; the sum on it is the settlement's captured
  amount, read from the ride, not the quote.
- AC: a driver with a rating of 3 fifty metres away sorts behind a driver with 5 a hundred metres away
  — or the research says what the distance-to-rating trade is and why.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/billing/PaymentGateway.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/GeoCandidateSource.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`

## What it turned out to be

**A tip is not a bigger number, it is a different mechanism — and saying so cost four small things
rather than one big one.** `capture` cannot exceed its hold, here or at a real provider, and the hold
was the quote and is gone by the time R8 is on screen. So the gateway grew `charge`, and everything
downstream of it needed its own key: the payout table's primary key became `(ride_id, kind)`, the
saga's row id became `<ride>:tip`, and the outbox event became `<ride>:tipped`. **The last one was
found by a test rather than by thinking**: the tip's settlement failed with a
`BatchUpdateException` wearing a saga's clothes — "settlement `<ride>:tip` failed systemically" —
because the outbox key is the idempotence of one settlement and a tip is a second one.

**The driver keeps the whole tip.** A platform cut of a tip is a policy, and a demo that invented one
would be teaching it. What the compensation refunds is the tip's *own* charge — the id the step left
in the enriched payload — and not the payload's hold, which for a tip is the fare the rider was happy
with.

**The rating is the first time the candidate sort's second key is not a constant.** It used to arrive
in the driver's own position frame, so the sorted party chose it; it is now the average of what riders
recorded, with the frame's value left as the fallback for a driver nobody has rated. What did *not*
change is the order — the item allowed either answer provided the research says why, and
[§1.6d](../research/research-architecture.md) does: any coefficient that makes "a three-star car
fifty metres away sorts behind a five-star one at a hundred" come out right is a number chosen to
make that one example come out right.

**Two guards did their jobs on the way past.** `GlyphCoverageTest` refused `★`/`☆` — the bundled face
has neither, and a character no bundled font can draw falls back to whatever the host has, which is a
different width and moves its neighbours; the stars are a vector now. And `SchemaTest` refused the
migration until the `DEFAULT 'FARE'` came back off the new column: a default that lives only in the
database is a rule the application cannot see. The fixture had its own version of the same
carelessness — `truncateAll` did not know about `ratings`, so one test's five averaged into the
next test's three and the assertion read 4.0.

- AC 1: `POST /api/rides/{id}/rating` and `/tip`, both on the rider's tier, both 409 before
  `COMPLETED`; the tip is a `charge` with a payout row of its own, and
  `a tip that dies before its payout gives the money back` is the B-11 shape for money with no hold.
- AC 2: `screens_rider_finished`, with the sum read from `RideView.chargedCents`. Light variants when
  [B-48](B-48-light-goldens-for-every-screen.md) lands, as with B-43's three.
- AC 3: answered by the research rather than by a coefficient, which is the branch the item offered.
  What the code does hold is that the number is the riders' — `a rating a rider gave is the rating
  dispatch sees` asserts a 3 where the socket reported 4.9.
