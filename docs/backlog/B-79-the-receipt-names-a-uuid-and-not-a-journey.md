---
id: B-79
title: "R9·b names a UUID and not a journey: no date, no addresses, no driver"
status: done
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-79 — R9·b names a UUID and not a journey: no date, no addresses, no driver

Live R9·b: `receipt / 62e4f2f8-c596-4b19-a6f7-93ebf024cd95 / $ 33.96 / economy · 22.8 km · 35 min /
fare · tip · paid with`. The kit's R9·b: `28 august / Lenina st, 14 · 19:40 pickup / Airport, terminal
B · 20:06 drop-off / base fare … total / Ivan Sokolov · Skoda Octavia · А 123 ВС · ★ 5`.

- **A ride id is not something a rider reads.** It is on the card because the tree needed a second
  line and the server had nothing else to hand; the kit puts the date there.
- **The date is the client's to format** (B-61's own rule), so it cannot be a text component in the
  tree — it has to arrive as an instant the client formats, or the receipt has to be drawn with a
  native header above the server's card.
- The addresses and the driver are on the ride (`pickup`, `dropoff`, `driver`) and the settlement
  payload; the stars the rider gave are on the ride too (B-59).
- Deliberately **not** covered: the kit's `base fare / 18.4 km / 26 min` lines — B-61 decided the
  receipt says what the settlements charged, not what the pricing formula would recompute.

- AC: the receipt shows when, from where to where, with whom and how it was rated, and no identifier.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/domain/ReceiptScreenUseCase.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderReceipt.kt`

## What it turned out to be

**Four lines the settlement already knew, and one the client had to say.** `SettledRide` carries
both ends as the settlement recorded them, the driver — name, car, plate, from the record B-63 gave
the server — and the stars this rider left; the tree names them in the kit's order: where, the
card, with whom, how rated. The identifier is gone from the screen; it had been there because the
tree needed a second line.

**The date is drawn by the client, over the tree.** B-61's rule stands — a date is a calendar and a
timezone and the browser has both — so it could not be a text in the tree. `ReceiptViewModel` reads
the ride beside the receipt and `RiderReceipt` draws `2 september · 19:40` as the one native line
above the server's card, in the same words R9's rows use.

**`rated 4 of 5` rather than a star.** The kit writes `★ 5`; the bundled face has no star, and the
glyph guard is the reason this product knows that before a golden on another machine does.

`ReceiptTreeTest` asserts the journey, the driver and the rating off the wire, and that no text on
it is the ride's id. Two goldens re-recorded and looked at.
