---
id: B-03
title: "The foundation values: shashki's ramp, spacing, ink and golden pin"
status: done
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

## Where it stands

**Done: the ramp, the spacing, the semantic colours, the theme and the pin.**

- `ShashkiTypography` carries the kit's seven names, because four of the seven pair a stock size with
  a weight the library pairs differently — and because `pageTitle` names two different things in the
  two vocabularies (54 / W200 here, Metro's 14 / W400 application title there). `toKvadrant` is the
  only place they meet, so kvadrant's own components draw in the same ramp rather than one nobody
  chose. The two figure styles are `tnum`; nothing else is, because only money and time change while
  a screen is up.
- `shashkiMetrics()` is 12 dp as drawn, `scale` at 1f so the theme cannot rescale the ramp behind us.
  **The tile sizes are solved against the canvas rather than scaled** — `KvadrantTile` reads all
  three from the metric set, and the library's are fixed sizes where the kit's grid is fitted to 390:
  small 82.5, medium 177, wide 366, which keeps the kit's "wide is 2:1, the others square".
- `ShashkiColors` names `KvadrantAccents.Red` and `.Green` rather than retyping their hexes.
- ~~AC: a fixture renders all seven kit styles.~~ Done — `foundation_type_ramp`, each line labelled
  with the size and weight it claims, so a diff against the kit's specimen is readable without a
  ruler. It verifies on Linux too, so B-02's answer holds for the new ramp and not just for the
  skeleton.
- ~~AC: `ShashkiMetrics().scale == 1f`.~~ Done, and stated in KDoc with what a scaled set would cost.

**The app bar is deliberately not set, and the file says so rather than omitting it.** The kit gives
48 / 1.5 / 26 against the library's 36 / 1.125 / 19.5 — the same 4/3 — but 48 dp is also
`touchTargetMin`, which the library already enforces *around* a 36 dp visual, so the kit's row may be
naming the target or the ring. Nothing draws an app bar yet; B-04 settles it against the artboard
rather than between two readings of a table.

## Closed by the publication

kvadrant-ui **0.2.0** landed on 2026-09-01 ([B-22](B-22-publish-kvadrant-ui-with-the-hooks.md)), and
the theme now passes `onAccent = Color.Black` through `KvadrantColors.dark()` and `.light()` — the
accent stays at the kit's hex and only the ink moves, which is the whole point of the parameter.
`skeleton_themes` re-recorded: **11 077 → 11 164 bytes, black on both accents**, and it verifies on
Linux under `--rerun-tasks`. The ramp golden is byte-identical, as it should be — it has no accent
surface in it.

- ~~AC: the ink on a filled accent surface is black, supplied through `KvadrantColors`' `onAccent`
  parameter — not through a shashki-local constant that shadows the library.~~ Done, on 0.2.0.
- AC: the app bar is stated through `KvadrantMetrics`' `appBar*` fields ([B-19](B-19-kvadrant-app-bar-tokens.md)),
  not by re-measuring it.
- AC: `ShashkiMetrics().scale == 1f`, and a test fails if a scaled set reaches `KvadrantTheme`.
- AC: `ShashkiMetrics` is `KvadrantMetrics(margin = 12.dp, tileGap = 12.dp, …)` with `scale` at 1f —
  the kit's numbers as drawn ([B-15](B-15-answer-the-kits-open-questions.md)) — and the constant
  carries a comment pointing at research §1.1c, which records that the evidence says Metro's own
  number is 9 dp and that the kit was chosen over it deliberately.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/`
