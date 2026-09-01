---
id: B-18
title: "kvadrant-ui: onAccent becomes overridable, keeping the computed value as the default"
status: open
priority: P0
size: XS
stage: stage-0-unknowns
---

# B-18 — kvadrant-ui: `onAccent` becomes overridable, keeping the computed value as the default

Research §1.1a and §1.1f. `KvadrantColors.onAccent` is `val onAccent: Color get() = contrastOn(accent)`
— a computed property, so no consumer can supply a different answer. `contrastOn` returns white below
a luminance of 0.5, and both of shashki's accents are below it: Cyan at 0.312 and Amber at 0.447. The
kit specifies black ink on both, and on Amber the computed value is about 2.2:1.

- **Additive, and the default does not move.** It becomes a constructor parameter of `KvadrantColors`
  defaulting to `contrastOn(accent)`. Nothing changes for a consumer that does not pass it; the
  library's own trade — the authentic Metro result, an accepted WCAG failure, its research D7 —
  survives as the default. A default that can be overridden is still a position.
- The rejected alternative is a shashki-local constant shadowing the library. It works and it puts
  the product's theme in two places, one of which the components read and one of which they do not.
- The other rejected alternative is changing the *default* to black. That would decide for every
  consumer, in order to suit one, against a rule the library documents as deliberate.
- **The price is stated:** `KvadrantColors` is a `data class` and kvadrant has `abiValidation`
  switched on, so this is a binary-incompatible change and arrives as a diff somebody approves. That
  is the mechanism working — those signatures have moved unnoticed before, which is why it is there.

- AC: `KvadrantColors.dark(accent = …, onAccent = Color.Black)` compiles and renders.
- AC: the existing goldens are unchanged, because the default is.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantColors.kt`
