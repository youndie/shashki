---
id: B-44
title: "Finished: rate the driver and tip, and the tip is a second charge"
status: open
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
