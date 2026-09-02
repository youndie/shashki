---
id: B-48
title: "Every screen fixture gains its light variant, which open question 1 promised"
status: open
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
