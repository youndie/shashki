---
id: B-18
title: "kvadrant-ui: onAccent becomes overridable, keeping the computed value as the default"
status: open
priority: P0
size: XS
stage: stage-0-unknowns
---

# B-18 — kvadrant-ui: `onAccent` becomes overridable, keeping the computed value as the default

**Filed upstream as kvadrant-ui B-48.** This item tracks shashki's dependency on it; the argument and
the acceptance criteria live there, framed for the library and its other consumers rather than for
this product.

Research §1.1a and §1.1f. `KvadrantColors.onAccent` is `val onAccent: Color get() = contrastOn(accent)`
— a computed property, so no consumer can supply a different answer. It returns white below a
luminance of 0.5, and both of shashki's accents are below it: Cyan at 0.312 and Amber at 0.447. The
kit specifies black on both, and on a filled accent tile carrying a fare that is the legibility of
the number.

**This item was briefly a question and is one again no longer.** An earlier draft proposed filing an
issue asking whether the 0.5 threshold was authentic — whether Metro really put white on a cyan tile.
It did. `contrastOn` is a faithful transcription, there is no defect, and the arithmetic about a
contrast-optimal threshold was a correct calculation attached to the wrong conclusion.

- **What is missing is an opt-in, not a fix.** The library already states the policy this falls
  under — canonical visual by default, higher-contrast variants opt-in (its B-11, its research D7) —
  and already ships one lever for it, `accessible()`, which reaches AA by *moving the accent*.
  shashki cannot use that: the kit's accents are its two fixed hexes. The lever that keeps the accent
  and changes the ink does not exist, and adding it is additive under a policy already written down.
- **The default does not move.** `onAccent` becomes a constructor parameter of `KvadrantColors`
  defaulting to `contrastOn(accent)`. Nothing changes for a consumer who does not pass it, and the
  authentic answer stays the answer.
- Rejected: a shashki-local constant shadowing the library. It works, and it puts the product's theme
  in two places, one of which the components read and one of which they do not.
- Rejected: changing the default to black. That decides for every consumer against a transcription
  the library is right to keep.
- Rejected: `accessible()`. It reaches AA by moving the accent off the kit's hex, which is the one
  thing the kit is not negotiable about.
- **The price is stated:** `KvadrantColors` is a `data class` and kvadrant has `abiValidation`
  switched on, so this is a binary-incompatible change and arrives as a diff somebody approves. That
  is the mechanism working — those signatures have moved unnoticed before, which is why it is there.

- AC: `KvadrantColors.dark(accent = KvadrantAccents.Cyan, onAccent = Color.Black)` compiles and
  renders, and the same for `light()` and for `copy()`.
- AC: every existing golden is byte-identical, because the default is unchanged.
- AC: the ABI dump is updated in the same commit as the signature.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantColors.kt`
