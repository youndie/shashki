---
id: B-05
title: "Every fixture string is checked for glyph coverage"
status: done
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

- ~~AC: a helper every fixture calls, and one deliberately broken fixture proving it fails.~~
  **Done, 2026-09-02, and not as a helper.** `GlyphCoverageTest` walks
  `GeneratedViddikRegistry.components` — viddik's own KSP output — composes each fixture and reads
  its text off the semantics tree, so nothing is opted into. The broken fixture is real: putting
  `₽` back into `ClassTiles` fails with `components/class tile: "249 ₽" — U+20BD`.
- Anchors: `viddik/README.md`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/foundation/KvadrantText.kt`

## What it turned out to be

**A guard nobody has to remember, because the thing that enumerates the fixtures is generated.**

The item asked for "a helper every fixture calls", and that shape was the weaker half of it: a
helper covers the fixtures written before it and silently misses the one added next month, which is
the same failure mode — something not happening and nothing saying so — that the item exists to
close. viddik's KSP processor already writes `GeneratedViddikRegistry.components` from the
`@ViddikScreenshot` annotations, so the guard composes every registered fixture and reads its
strings off the semantics tree. A fixture added tomorrow is checked tomorrow.

The rule is the one `KvadrantText` actually applies rather than a tidier version of it: Cyrillic and
`U+25CF` are routed to the Source Sans companion, everything else must be in Selawik. That is the
library's own approximation, `U+25CF` included — the character that fell out of both families once
and let the host draw the password mask. Checking against the same approximation is deliberate: a
guard that used a better rule than the renderer would pass strings the renderer still gets wrong.

**Three ways it could have passed for the wrong reason, all closed.**

- *The semantics walk returning nothing.* Checked per fixture, not in total: exactly two fixtures
  may draw no text — the two canvas tiles — and they are named. Any other silent fixture fails.
- *Never meeting the characters it exists for.* The union of drawn text must contain `č`, which
  only `Miklošičeva cesta 4` in the type ramp supplies.
- *The checker rejecting nothing.* `✕` must be reported as `U+2715`, and the real control is the
  one the AC asked for — `₽` in a fixture, failing by name.

**The fixture tile carries no diacritic, and that is worth knowing.** The map's labels come from
data rather than from literals, so they get their own check — and pinning it showed the tile's four
labels are `Voglje`, `Vodice`, `Torovo`, `A2`, all ASCII. On its own that test would prove nothing
about a city whose street names are full of diacritics; the diacritics are carried by the ramp
fixture, and the whole extract's alphabet was checked once by hand in
[B-06](B-06-city-extract-and-tiles.md). The test is still worth having — it is the only string in
the project that comes from the archive — but it is not the diacritic guard, and saying so here
stops the next reader from believing it is.

**Two things checked in passing.** The five Selawik weights are held to identical coverage, because
the guard uses one of them to stand for all five and that is only sound while they agree. And
`compose.uiTest` resolves to 1.12.0, the same line as the rest of the stack — research §1.2's
`NoSuchMethodError` risk is about exactly this, and the cache held a 1.11.1 from elsewhere.
