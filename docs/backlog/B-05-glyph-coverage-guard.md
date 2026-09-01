---
id: B-05
title: "Every fixture string is checked for glyph coverage"
status: open
priority: P1
size: XS
stage: stage-0-unknowns
---

# B-05 — Every fixture string is checked for glyph coverage

Almost every fixture in the handoff carries `₽` (U+20BD): 249/389 ₽, 420 ₽, 26 940 ₽. viddik's
documentation is explicit that a character the bundled font does not cover falls through to a *host*
font, which makes the golden a record of the machine that took it — and the failure is silent, since
the screenshot still renders.

- **Make it a fixture failure, not a warning.** `ViddikGlyphCoverage.missingGlyphs(text)` reads the
  font's own `cmap`; a non-empty result fails.
- The rejected alternative is checking once by hand. The set of strings grows with every fixture, and
  a character added in six months arrives with no check attached.
- Covers the bundled Selawik and the bundled Source Sans 3, since `KvadrantText` routes per
  character between them.

- AC: a helper every fixture calls, and one deliberately broken fixture proving it fails.
- Anchors: `viddik/README.md`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/foundation/KvadrantText.kt`
