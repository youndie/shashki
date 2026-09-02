---
id: feature-settlement
title: Settlement — capture, payout, receipt, events
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries: []
api:
  - endpoint-rides
tags: [saga, compensation, money]
---

# Settlement

## 1. Overview

When a trip ends, somebody has to be charged and somebody has to be paid. That is the product's
**second** saga: capture the hold, record the payout, send the receipt, publish what happened — five
phases with real compensations, because a payment that has moved can only be undone by giving it
back.

The same saga runs when a rider cancels after a driver was assigned. Same five phases, one different
number.

## 2. Business rules

* A fare captures what was held. A cancellation captures a **quarter** of it and the rest is never
  taken.
* The driver is paid the charge minus the platform's cut — twenty per cent — so a cancellation pays a
  share of the fee rather than a share of the fare.
* A hold is captured **exactly once**. A second capture of the same hold is refused by the gateway
  rather than charged.
* A charge that rounds to nothing is not settled at all: a payment of zero is a line on somebody's
  statement for no reason.
* The receipt goes to the address on the rider's token. No provider means no address, and the
  settlement records that it sent none rather than inventing a recipient.
* **A mail server that is down does not undo a payment.**

## 3. Flow

1. `AdvanceTripUseCase` (or `CancelRideUseCase`) starts the saga under `<rideId>:settlement` — a
   separate row, because the order saga already occupies `rideId` and overwriting it would lose the
   record of how the ride was assigned.
2. **ENRICHMENT** works out the charge and the payout. **VALIDATION** refuses if there is nothing to
   settle. **AUTHORIZATION** captures — compensation is a *refund*. **EXECUTION** writes the payout row
   — compensation removes it. **POST_PROCESSING** sends the receipt and writes `ride.settled` into the
   outbox.
3. The relay delivers the event to booblik; a consumer builds the ride's history from it.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/settlement/` |
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/billing/PaymentGateway.kt` |
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/` |

## 5. Scenarios

### Scenario: a fare is settled

* **Given:** a trip driven to `COMPLETED`
* **When:** the settlement runs
* **Then:** the whole hold is captured, the driver's payout is 80 % of it, the receipt is sent and one
  `ride.settled` event is in the outbox — and no hold is left active
* **Automated:** `shashki SettlementSagaTest`

### Scenario: the process dies between any two phases

* **Given:** a settlement part-way through
* **When:** the next step never returns
* **Then:** **no money stays taken and no payout stays standing** — the capture is refunded and the
  row removed
* **Automated:** `shashki SettlementSagaTest`

### Scenario: a settlement the first process abandoned

* **Given:** a row parked after the capture committed, with the money already moved
* **When:** a second process picks it up
* **Then:** it continues at EXECUTION and **takes nothing more**; a second capture of the same hold
  throws rather than charging twice
* **Automated:** `shashki SettlementSagaTest`

### Scenario: the rider cancels after a driver was assigned

* **Given:** an assigned ride
* **When:** the rider cancels
* **Then:** a quarter of the fare is captured and the rest is released — against a cancellation before
  a driver, where nothing is charged at all
* **Automated:** `shashki SettlementTest`

### Scenario: the mail relay refuses the connection

* **Given:** a settlement whose receipt cannot be sent
* **When:** it runs
* **Then:** the money and the payout are exactly where a successful send would have left them, and the
  saga records `receipt.sent = false`
* **Automated:** `shashki SettlementSagaTest`

## 6. Out of scope

* A real payment provider. `InMemoryPaymentGateway` is the brief's mock cashier; what is real is the
  integration contract — hold, release, capture with an amount, refund.
* Paying the driver. The payout is a row; a transfer is a batch somebody runs against a bank.
* Invoices, tax, currency other than the one the quote names.

## 7. Quirks

* **`capture` takes an amount, and the first version did not.** Capturing the whole hold is right for
  a fare and charges a rider the entire journey for a car they sent away. The fee test caught it.
* **Everything the saga needs is copied into its payload, not looked up.** Reading the order saga's
  row at every phase would make this saga's behaviour depend on a row somebody else may still be
  writing; what is owed was decided when the trip ended.
* **The receipt feature existed for weeks with no DI module.** It was written, tested against a real
  SMTP server, and constructed by nobody — see [B-14](../backlog/B-14-receipt-over-smtpkn-jvm.md) and
  [B-37](../backlog/B-37-the-settlement-saga.md).
