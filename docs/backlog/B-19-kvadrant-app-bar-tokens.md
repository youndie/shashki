---
id: B-19
title: "kvadrant-ui: the app bar's dimensions become theme tokens"
status: open
priority: P1
size: S
stage: stage-0-unknowns
---

# B-19 — kvadrant-ui: the app bar's dimensions become theme tokens

**Filed upstream as kvadrant-ui B-49.** This item tracks shashki's dependency on it; the argument,
the acceptance criteria and the work live there. What is recorded below is why shashki needs it and
what was checked before filing.

Research §1.1d and §1.1f. `HEIGHT`, `MINI_HEIGHT`, `BUTTON` and `RING` are `private val`s inside
`KvadrantAppBar.kt`, and `KvadrantAppBarGlyphSize` is a public top-level constant rather than a theme
token. Two consequences, and the second is a defect in the library rather than a shashki problem:
a theme cannot state the app bar's numbers at all, and a theme scaled with `scaled()` moves the whole
page around a bar that stays put.

- **The numbers already exist upstream of the code.** `metro-tokens.json` in kvadrant's reference
  brief carries `appBarHeightPx` 72, `appBarMiniHeightPx` 30, `appBarIconPx` 48 and `appBarGlyphPx`
  26 — the exact values the component restates by hand in its `// 72 px` comments. `KvadrantMetrics`
  is hand-transcribed from that same file (the generator covers colours, accents and font sizes, not
  metrics), and these four were transcribed into the component instead. A gap between the token
  source and the token surface, not a proposal.
- **Move them into `KvadrantMetrics`,** where `scaled()` already carries every other screen distance,
  with today's values as the defaults. The kit can then state its own; the scale bug goes away for
  everyone.
- The rejected alternative is scaling them inside the component from `KvadrantTheme.metrics.scale`.
  It fixes the scaling half and leaves the bar unstateable, which is the half shashki needs.
- Deliberately not covered: `tiltDepression`, which is documented as correctly unscaled and has a
  test that fails if anyone adds a factor to it.

- AC: a theme built with `KvadrantMetrics(appBarHeight = …, appBarButton = …)` moves the bar.
- AC: `scaled(f)` moves the app bar with the rest, and a golden at a scaled theme shows it.
- AC: the defaults are today's numbers, so every unscaled golden is byte-identical.
- **Checked, and the answer is one image.** `app_sample_window` (`SampleWindow.kt`, group `app`,
  560×860) is the only golden rendered through `KvadrantMetrics().scaled(1.6f)`, and it contains a
  `KvadrantAppBar`. It will move — bar 54 → 86.4 dp, button 36 → 57.6, ring 1.125 → 1.8, glyph
  19.5 → 31.2 — because the whole point of the change is that the bar starts scaling. Every other
  golden is at scale 1f and must come out byte-identical.
- AC: that one golden is re-recorded deliberately, named in the PR body with the numbers above, and
  no other golden moves. A PR that silently rewrites a reference is the failure this AC prevents.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/KvadrantAppBar.kt`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantMetrics.kt`
