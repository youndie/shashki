---
id: B-62
title: "R4 prices a class it has just said has no cars"
status: done
priority: P2
size: XS
stage: stage-6-what-running-it-said
---

# B-62 — R4 prices a class it has just said has no cars

On the stand with nobody online, all three class tiles read `no cars nearby` **and** carry a price:
`$ 28.96`, `$ 43.43`, `$ 63.70`. The kit's R4 draws the unavailable class with `—` where the price
goes, and the reason is in the tile's own contract: *unavailable (inactive-текст, «—» вместо цены)*.
A price beside "no cars nearby" is an offer the product cannot honour, and the order bar underneath
it still says `order · $ 28.96`.

- **A price is a promise about a ride that can be had.** The quote is real — the road was routed and
  costed — but with no candidate there is nothing to buy, and the tile that says so should not also
  quote. `ClassTile` already has the state; the screen passes it a figure anyway.
- The rejected alternative is hiding the class. The kit keeps it: a rider learns that business exists
  and is empty right now, which is worth more than a shorter list.
- Deliberately **not** covered: what the app bar does when *every* class is unavailable. Today it
  offers to order the first one; the kit's answer is R5·a's *no cars nearby* page, and that is
  [B-43](B-43-the-rider-sees-the-wait-and-its-end.md)'s territory.

- AC: a class with no candidate draws `—` rather than a figure, in the inactive brush, and the golden
  shows it.
- AC: ordering is not offered for a class that has no cars.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/ClassTile.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/classpicker/`,
  `docs/screens/screen-rider-class-picker.md`

## What it turned out to be

**One `takeIf`, and the tile had been right all along.** `ClassTile` draws the kit's em dash whenever
it is given no price and refuses to be clicked when unavailable; the screen was handing it a price
anyway, so on a stand with nobody online all three rows read `no cars nearby · $ 28.96`. The quote is
real arithmetic and stays — the bar still needs it — but a class nobody is driving does not carry a
figure.

**The bar was the same defect one row down.** It said `order · $ 28.96` for a class it could not
order. It says `no cars nearby` now, the tick is drawn in the disabled brush and pressing it does
nothing, which is the kit's own shape for an action that is not available.

**And the state a stand is in most of the time now has a golden.** `rider class picker empty` is
every class unavailable — three em dashes and a bar that refuses. Nothing had photographed it, which
is why nobody had seen the price standing beside the sentence that contradicts it; the bundle's own
"before the server answers" golden moved with the same change, and shows `order` in the disabled
brush rather than an offer to buy something that has not been priced yet.

`ClassOfferTest` holds the mapping to it: no candidate, no price, not available.
