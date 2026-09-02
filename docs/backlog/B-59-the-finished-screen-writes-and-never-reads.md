---
id: B-59
title: "R8 asks for a rating it already has, and puts its one accent on skip"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-59 — R8 asks for a rating it already has, and puts its one accent on skip

Opened against a ride that was rated five and tipped, `/finished/{id}` on the stand draws five empty
stars, no tip selected, and *skip* filled in the accent. Four things separate it from the kit's R8:

* **The rating is write-only.** The ride carries one and the screen does not read it back, so a
  refresh — or a pasted link — invites a second rating of the same ride, which the server accepts.
* **The one accent surface is on the "no thank you".** `selectedTip == null` means *skip*, and the
  chip paints itself accent when selected, so the screen opens recommending that nothing is paid.
  The kit's rule is one accent surface per screen and R8 spends it on the tip; spending it on the
  refusal is not what "skip is a first-class button" was meant to buy.
* **No total with the tip.** The kit's R8 ends with `total with tip · 470 ₽`; here the fare stands
  alone and a rider who taps `$ 5` sees no number change. The tip is a separate charge (that is
  [B-44](B-44-finished-rate-and-tip.md)'s decision and a good one), which is exactly why the screen has to
  add up what was actually taken.
* **The meta is short of the kit's.** `airport · 4.1 km` against `paid with card ·· 4417 · 26 min ·
  18.4 km` — the payment method and the duration are on the ride and are not drawn.

- **The decision is that a finished ride is a record before it is a form.** What was paid, with what,
  how long it took, what was already said about the driver — and *then* whatever is still open. A
  screen that opens as a blank form every time is a screen that does not know the ride happened.
- The rejected alternative is disabling the stars once rated. Showing them filled says more, costs
  the same, and matches "rating writes on tap, no confirm step".
- Deliberately **not** covered: editing a rating. Read it back, draw it, and leave changing it to an
  item that has decided whether a driver's average may move twice.

- AC: reopening a rated ride shows the stars it was given and does not offer to rate it again.
- AC: the accent is on the tip that is selected, and nothing is selected when the screen opens.
- AC: the screen names the payment method and the duration, and shows the total when a tip is chosen.
- Anchors: `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/finished/`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderFinished.kt`,
  `docs/screens/screen-rider-finished.md`

## What it turned out to be

**Four small things, and three of them were the screen asserting something it did not know.**

**The rating is read back.** `RideView` carries `stars`, filled from the ratings table — the row was
there since B-44 and nothing asked it — so a rated ride opens with its own stars and `Done` does not
send them again. The guard is one assertion: a ride carrying four opens on four and rates nothing,
and it fails against the old code.

**Nothing is chosen when the screen opens.** `selectedTip == null` used to *mean* skip, so the
refusal was filled in the accent the moment the screen appeared — the one accent surface the kit
allows a screen, spent recommending that nothing be paid. `skipped` is its own state now, and
skipping is a thing somebody does.

**The total appears with the tip.** Adding the charge to the chip a rider just pressed is not
pricing: `FareBreakdown`'s rule — the server sends lines, the client computes no total — is about a
receipt, whose lines are the settlement's. A screen that changed nothing when a tip was chosen was
showing a button, not a price.

**And the meta is the kit's.** `paid with card-4417 · 35 min` beside the destination. The payment
method is the id the request carried and not a card number, because this product has no card and
printing digits it does not have is the fabrication B-47 refused in another place.

The golden was re-recorded and looked at: three filled stars, `$ 5` accented, `skip` not, and
`total with tip · $ 33.96` under the row.
