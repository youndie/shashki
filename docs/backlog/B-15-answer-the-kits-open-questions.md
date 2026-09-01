---
id: B-15
title: "Settle the kit's 4/3 spacing: as drawn, as converted, or fitted"
status: done
priority: P1
size: XS
stage: stage-0-unknowns
---

# B-15 — Settle the kit's 4/3 spacing, and the three the kit asked

Three questions the kit addressed to the client side, and a fourth the research added on finding
that every spacing number in the kit is exactly 4/3 of the library's while its type ramp is exactly
1:1 — 4/3 being 1 / 0.75, the kit's own stated px → dp factor.

## What it turned out to be

- ~~AC: four answers written into the research as decisions, and the fixture list updated to
  match.~~ Done, research §3 open question 1.
- ~~AC: `ShashkiMetrics`' spacing constant set from the answer rather than from the kit's table.~~
  Done — and the answer was to take the kit's table after all.

**The spacing is 12 dp, as drawn, and the evidence says that is not Metro's number.** The token dump
carries `pageMarginPx` 12 and tile `gapPx` 12, which convert to 9 dp; the kit lists 12 and labels it
dp. A deliberate 4/3 scale-up landing exactly on the pixel column for spacing while leaving type on
the dp column is not a story that holds together, so the likeliest reading is a skipped conversion.
It is shipped anyway: the kit is this product's design authority, the artboards are what the goldens
are diffed against, and the look was approved at 12. Metro fidelity is the library's job. The
constant carries a comment saying all of this, so nobody re-derives it and quietly "fixes" it to 9.

The third option, `scaledToWidth(390.dp)`'s 10.26 dp, is the one that looks principled and is not:
the same call scales the type ramp by 1.14 and puts the page title at 61.6 sp, which the kit's own
invariant forbids. Taking the number without the mechanism invents a fourth number.

**The app bar keeps its shape.** No filled accent accept button in the bar, and the offer screen does
not suppress it — simplify for now, so `DriverOffer` and `DriverArrived` are built from what the kit
already draws rather than from a new variant.

**The light theme is kvadrant's stock `light()`,** verified by goldens rather than awaited. That
doubles the fixture set: every fixture gains a light variant, and the light half stops being
unscoped work.

- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantMetrics.kt`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantColors.kt`
