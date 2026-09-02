---
id: B-48
title: "Every screen fixture gains its light variant, which open question 1 promised"
status: done
priority: P1
size: S
stage: stage-5-the-rest-of-the-kit
---

# B-48 — Every screen fixture gains its light variant, which open question 1 promised

Research open question 1 answered the kit's third question — is the light theme kvadrant's stock
light or does it wait for the kit's next pass — with **stock `KvadrantColors.light()`, verified by
goldens**, and wrote "the suite doubles: every fixture gains a light variant". It did not double.
`grep -l light shared-ui/src/desktopTest` finds `StockComponentFixtures` and the map tile; every
screen fixture — class picker, trip, promo, shift, offer, assigned ride — is dark only. So the light
theme is verified for a list row and a button, and assumed for every screen a person would open.

- **A variant per fixture, not a second fixture set.** `RiderTheme(dark = false)` is one parameter;
  the fixtures are parameterised on it and viddik records both. The kit drew light only for the
  map, so these goldens are the first pictures of the product's light screens anyone has seen — they
  are recorded, looked at, and *then* trusted, which is B-02's rule.
- **What is being checked is the kit's claim, not the library's.** kvadrant transcribed both themes
  and neither is derived from the other; the kit's light basemap was designed against that. Whether
  a `ClassTile` on the light chrome still reads at the accent's 2.90:1 on white is exactly the number
  the kit's accent page printed and nobody has looked at on a screen.
- The rejected alternative is leaving it. The research says the light half is scoped work; scoped
  work with no item is the state B-32 and B-37 were found in.

- AC: every `@ViddikScreenshot` under `screens` and `components` has a light twin, recorded on the mac
  and verified on Linux by the same `check`.
- AC: any fixture that comes out unreadable in light — an accent figure that fails on white, a
  semantic colour that vanishes — is fixed in `ShashkiTheme` or reported to kvadrant, and the research
  says which.
- AC: the map's light style is used by the light trip screen, not the dark one — the two palettes are
  the styles' own, and a light screen on a dark basemap is the defect this item would most easily
  ship.
- Anchors: `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/ScreenFixtures.kt`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/ComponentFixtures.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/ShashkiTheme.kt`

## What it turned out to be

**The doubling was mechanical and the point of it was the one image nobody had seen.** Every fixture
became a body taking `dark: Boolean` with two annotated twins over it — 21 light goldens across
`screens`, `components`, and both bundles' own — recorded on the mac and verified on Linux by the
same `check`, which is B-02's rule and the reason "recorded, looked at, then trusted" is possible at
all.

**One real defect, and it was where the item guessed it would be.** The offer card's fare is drawn in
the accent; the kit drew that card in dark only, where amber on black is 7.24:1. On white the same
amber is **2.11:1** — the worst pair in the palette, on the one figure a driver has fifteen seconds
to read. The fix is in the component and not in `ShashkiTheme`: the accent is the kit's, and moving
it would be this product editing the design it exists to demonstrate. The rule is one line —
**accent-coloured text is a control's label; figures take the foreground brush** — and the card still
leads with the accent through its strip and its accept button, which are *surfaces* with black ink at
9.95:1.

**One thing that was already right, checked rather than assumed.** `onAccent = Color.Black` in both
themes: the ink on a cyan tile measured 7.24:1 out of the recorded PNG rather than out of the
KDoc — the pixel under the `$ 5` label is `#000000` on `#1BA1E2`. The library's own `contrastOn`
would have picked white there, at 2.90:1.

**And one thing recorded rather than fixed**: a bar's label in accent on the light chrome is 2.13:1.
It is a control with a shape and a position, the kit's palette used as the kit uses it, and the
research says so beside the numbers.

- AC 1: done for `screens`, `components`, and — beyond the letter of the item — the `rider` and
  `driver` fixture groups, because "every screen a person would open" is what the headline says.
- AC 2: the fare, above. The full table of measured ratios is in
  [open question 1](../research/research-architecture.md).
- AC 3: the light trip screen already used `TilePalette.Light`, and the two bundles' `Fixture`
  wrappers now pair the palette with the theme rather than hard-coding the dark one — a light screen
  on a dark basemap is the defect this item would most easily have shipped.
