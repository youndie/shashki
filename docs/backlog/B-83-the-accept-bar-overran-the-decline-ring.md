---
id: B-83
title: "The offer's accept bar is 293 dp where the kit caps it at 200, and overruns the decline ring"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-83 — The offer's accept bar is 293 dp where the kit caps it at 200, and overruns the decline ring

Seen on the running desktop driver (2026-09-03): D3's *accept* reads as shoved to the left. Measured
off the golden rather than judged by eye — `components_offer_card.png`, 390 dp wide:

| | before | the kit |
|---|---|---|
| accept bar | x 48…340, **293 dp wide** | `flex: 1; max-width: 200px` |
| air to its left | 0 — it starts **inside** the decline ring's 48 dp box (12…60) | a share of the leftover |
| air to its right | 38 dp | the same share |

- **`weight(1f)` hands a child a fixed width, and `widthIn(max = …)` cannot narrow a fixed
  constraint.** So the cap was written, was compiled, and did nothing; the bar grew to fill its
  slot, the three children came to 389 dp inside a 366 dp strip, and `Arrangement.SpaceAround`
  distributed *negative* leftover by overlapping them.
- The trailing `Spacer` is the ring's own width on purpose — it is what makes the centring symmetric
  about the card rather than about whatever is left after the ring.

- AC: the bar is capped at the kit's 200 dp and centred in the strip, with real air on both sides of
  it and none of it under the decline ring.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/OfferCard.kt`

## What it turned out to be

**The weight moved to a wrapper.** `Box(Modifier.weight(1f), contentAlignment = Center)` takes the
slot; the bar inside is `widthIn(max = 200).fillMaxWidth()`, so it obeys the cap and is centred in
the space between the ring and the trailing spacer — and on a narrower screen it simply fills the
slot rather than overflowing.

Measured again on the re-recorded golden: **x 95…293, 199 dp wide, centre 194.0 of 390** — the
card's own centre — with 95 dp of air on the left, 96 on the right, and the decline ring back at
12…47 with nothing on top of it. Six goldens re-recorded, all six of them this card.
