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

**The defect this was written for is gone; the mechanism is not.** Research §1.2c read the bundled
`cmap`s and found no Selawik face carries `₽` (U+20BD), so every price would have been drawn by a
host font. The answer was to price in `$`, which all five faces cover — along with `·`, `—`, `×` and
`…`, the kit's other non-alphabetic characters. Nothing in the kit falls through today.

- **Guard the next one, because this one was silent.** `KvadrantText` splits by script, and the
  library's own KDoc says the real rule is coverage: "anything… that is neither Latin nor Cyrillic
  has to be checked against Selawik before it is drawn", with `U+25CF` as the character that already
  fell out of both runs once. A screenshot of a missing glyph still renders.
- `ViddikGlyphCoverage.missingGlyphs(text)` reads the font's own `cmap`; a non-empty result fails the
  fixture rather than warning.
- The licence plate was the same trap inverted — the kit's `А 123 ВС 177` was Cyrillic А/В/С — and is
  resolved by the plate becoming European Latin (§1.2d). One run, one font.

- The fixtures now carry the city's diacritics rather than avoiding them:
  [B-06](B-06-city-extract-and-tiles.md) put `Miklošičeva cesta 4` into the type ramp and checked
  the alphabet of all 3 629 street names in the extract — `ć Č č ř Š š Ž ž` and an en dash — against
  the two faces the fixtures draw with. All covered. That is the same check this item automates, run
  once by hand over one input; a guard is what makes it run over the next string too.

- AC: a helper every fixture calls, and one deliberately broken fixture proving it fails.
- Anchors: `viddik/README.md`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/foundation/KvadrantText.kt`
