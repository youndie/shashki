---
id: B-19
title: "kvadrant-ui: the app bar's dimensions become theme tokens"
status: open
priority: P1
size: S
stage: stage-0-unknowns
---

# B-19 — kvadrant-ui: the app bar's dimensions become theme tokens

Research §1.1d and §1.1f. `HEIGHT`, `MINI_HEIGHT`, `BUTTON` and `RING` are `private val`s inside
`KvadrantAppBar.kt`, and `KvadrantAppBarGlyphSize` is a public top-level constant rather than a theme
token. Two consequences, and the second is a defect in the library rather than a shashki problem:
a theme cannot state the app bar's numbers at all, and a theme scaled with `scaled()` moves the whole
page around a bar that stays put.

- **Move them into `KvadrantMetrics`,** where `scaled()` already carries every other screen distance,
  with today's values as the defaults. The kit can then state its own; the scale bug goes away for
  everyone.
- The rejected alternative is scaling them inside the component from `KvadrantTheme.metrics.scale`.
  It fixes the scaling half and leaves the bar unstateable, which is the half shashki needs.
- Deliberately not covered: `tiltDepression`, which is documented as correctly unscaled and has a
  test that fails if anyone adds a factor to it.

- AC: a theme built with `KvadrantMetrics(appBarHeight = …, appBarButton = …)` moves the bar.
- AC: `scaled(f)` moves the app bar with the rest, and a golden at a scaled theme shows it.
- AC: the defaults are today's numbers, so every existing golden is byte-identical.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/KvadrantAppBar.kt`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantMetrics.kt`
