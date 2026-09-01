---
id: B-19
title: "kvadrant-ui: the app bar's dimensions become theme tokens"
status: done
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

- **Four of the five numbers already exist upstream of the code.** `metro-tokens.json` in kvadrant's
  reference brief carries `appBarHeightPx` 72, `appBarMiniHeightPx` 30, `appBarIconPx` 48 and
  `appBarGlyphPx` 26 — the exact values the component restates by hand in its `// 72 px` comments.
  `KvadrantMetrics` is hand-transcribed from that same file (the generator covers colours, accents
  and font sizes, not metrics), and these four were transcribed into the component instead.
- **The ring is the fifth and has no source**, which a check of the upstream item found after this
  one was written: `metrics.windowsPhone` carries no 1.5 anywhere. Inside a component that is a
  detail; as a token it needs either a citation or an admission that the number is the library's own.
  kvadrant B-49 now carries that as a blocking question, so this item inherits it — shashki cannot
  state an app bar until it is settled there.
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

## What it turned out to be

**Done upstream: kvadrant-ui B-49, merged.** The five measurements are `appBarHeight`,
`appBarMiniHeight`, `appBarButton`, `appBarGlyph` and `appBarRing` on `KvadrantMetrics`, and
`scaled()` carries them. `KvadrantAppBarGlyphSize` is deprecated rather than deleted, and `-Werror`
named all three call sites the moment it was.

**The ring's provenance is settled, and the answer is that it is nobody's transcription.** Checked
against the WP8 SDK's design assembly, which carries ten control templates and does not include the
ApplicationBar — on the phone it was a shell control, not a XAML one — so there is no template to
read and 1.5 px is the library's own number, marked as such in KDoc. This item inherited that
question as blocking; it is closed, and shashki can state an app bar.

`app_sample_window` moved as predicted, and a defect nobody predicted came out with it: the fixture's
stand-in glyphs were `Small` tiles filling the button, covering the ring the button is made of. It
pre-dated the branch and a bigger button made it visible.

The change is **unreleased**, like B-18's.
