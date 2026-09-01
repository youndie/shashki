---
id: B-03
title: "The foundation values: shashki's ramp, spacing, ink and golden pin"
status: open
priority: P0
size: M
stage: stage-0-unknowns
---

# B-03 — The foundation values: shashki's ramp, spacing, ink and golden pin

The kit calls its foundation "inherited". Research §1.1 found that colour is, and type and spacing
are not: four of seven type pairings are new weights on stock sizes, `pageTitle` collides with a
stock slot that means something else (14 sp / W400, Metro's `ApplicationTitle`), and every spacing
number in the kit is exactly 4/3 of the library's — 1 / 0.75, the kit's own px → dp factor — while
its type ramp is exactly 1:1. Reaching for the scale knob to reconcile them rescales the ramp too.

- **Values supplied to the library, not a fork of it.** `KvadrantTypography` and `KvadrantMetrics`
  are `data class`es with public constructors and `KvadrantTheme` takes both, so `ShashkiTypography`
  and `ShashkiMetrics` are arguments — the same relationship the library already has with `accent`.
  `ShashkiMetrics` keeps `scale` at 1f so the ramp is not rescaled behind our back.
- **The ink is a value we can supply now.** [B-18](B-18-kvadrant-overridable-on-accent.md) landed
  upstream: `KvadrantColors.dark(accent = …, onAccent = Color.Black)`. shashki passes black and stops
  there — no local constant shadowing the library.
- `negative` / `positive` are `KvadrantAccents.Red` / `.Green` by name, not by hex.
- Also here: the local `portable()` over `ViddikPlatformTextStyle`, because kvadrant's is `internal`
  and in `desktopTest`, and shashki builds its ramp by hand rather than through
  `KvadrantTypography.default`.

- AC: a fixture renders all seven kit styles and the golden matches the kit's specimen.
- AC: the ink on a filled accent surface is black, supplied through `KvadrantColors`' `onAccent`
  parameter — not through a shashki-local constant that shadows the library.
- AC: the app bar is stated through `KvadrantMetrics`' `appBar*` fields ([B-19](B-19-kvadrant-app-bar-tokens.md)),
  not by re-measuring it.
- AC: `ShashkiMetrics().scale == 1f`, and a test fails if a scaled set reaches `KvadrantTheme`.
- AC: `ShashkiMetrics` is `KvadrantMetrics(margin = 12.dp, tileGap = 12.dp, …)` with `scale` at 1f —
  the kit's numbers as drawn ([B-15](B-15-answer-the-kits-open-questions.md)) — and the constant
  carries a comment pointing at research §1.1c, which records that the evidence says Metro's own
  number is 9 dp and that the kit was chosen over it deliberately.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/`
