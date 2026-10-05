---
id: B-101
title: "kompot 0.39: the new group, the degradation outcome and the 0.38 vocabulary"
status: done
priority: P2
size: M
stage: stage-3-surface
---

# B-101 — kompot 0.39: the new group, the degradation outcome and the 0.38 vocabulary

The build was on `kompot = "0.36.2.116"`. Two releases of the toolkit had landed since, none of
it used here ([#43](https://github.com/youndie/shashki/issues/43)). What changes for this
repository is written in kompot's `UPGRADING.md` (0.38.0 and 0.39.0) and SPEC.md §4.7–§4.12.

- **The coordinate.** Since 0.38 every artefact is under `io.github.youndie.kompot`. The settings
  plugin's repository filter is a regex over `io.github.youndie` and its subgroups, so it admits the
  new group as it stands; the version is a CI-numbered build from the Reposilite, because 0.39.0 is
  not on Central yet.
- **The degradation report carries kompot's outcome.** `KompotDegradationSink` now says what was
  drawn — `NOTHING`, `PLACEHOLDER`, `SERVER_FALLBACK` — where it said `drawnAsFallback`, and that
  boolean was `true` for a missing renderer and for the server's equivalent alike.
- **The vocabulary.** Headings, roles and labels for a screen reader; `divider`; impressions counted
  on visibility.
- Rejected: moving Compose, viddik or kvadrant with it. kompot 0.38+ is built on Compose 1.12.1,
  which is already the line `wip` holds here, so the Compose half is one line without any of them
  moving; nothing in this repository uses `kompot-studio`, the one module that needs viddik 0.6.

- ~~AC: the build resolves kompot `0.39.0.<run>` under the new group.~~ **Done:** `0.39.0.201`.
- ~~AC: `DegradationReport` carries kompot's outcome end to end, the server's count included.~~
  **Done.**
- ~~AC: at least one server-sent screen uses a word kompot now has, or this says why none applies.~~
  **Done:** the receipt's `divider`; see below for the honest size of it.
- **Not here:** `0.39.0` from Central. kompot has not cut it; when it does, the move is one line in
  the catalogue, and #43 stays open until then.
- Anchors: `gradle/libs.versions.toml`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/ScreenTokens.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/promo/data/ReportingDegradationSink.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/Degradations.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/domain/ReceiptScreenUseCase.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/Renderers.kt`

## What it turned out to be

**The outcome, end to end.** `DegradationReport.outcome` is kompot's enum by name — a string, like
`kind`, because the enum lives in `kompot-client`, which carries Compose and has no place in
`:protocol`. `DegradationCounter` counts by kind and type as before and by outcome within them, and
its log line says what was drawn. The one thing that could have gone wrong quietly is the old field:
the server decodes strictly, so a DTO that simply lost `drawnAsFallback` would answer every report
from a bundle built before this change with a 400, and the sink swallows failures by design — the
reports would have stopped with nothing saying so. The field stays on the DTO, read and never
written, and such a report is counted as `UNREPORTED`, because its `true` cannot be split after the
fact. `DegradationRoutesTest` posts that older body as raw JSON; `ReportingDegradationSinkTest` sends
each of the three outcomes and decodes each with the server's class.

**A word kompot now has, and how big that is.** The three candidates #43 named were a ride-option
rail, a cancel confirmation and a receipt divider. The first two are on native screens — the class
picker and the trip screen are Compose, not trees a server sends — so a kompot word cannot replace a
shape there without making those screens server-driven, which is a product decision (research §2
D11), not a vocabulary swap. The two server-sent screens, promo and receipt, contained no hand-built
shape to drop. What the receipt did lack is the rule the trip screen hand-draws above the same driver
(`DriverRow`: a 1 dp box at the ink's 12 %); the tree had no way to say "a rule" before 0.38 short of
an empty column with a size and a background. It now says it as `divider`, above who drove.

**Two defects in kompot, filed rather than absorbed.**

- [youndie/kompot#204](https://github.com/youndie/kompot/issues/204) — a `divider` with no colour is
  drawn in Material's `outlineVariant`, and `KompotDesignSystem` has no hook to change it. The
  workaround is a `hairline` token in `ShashkiTokens`, resolved to the native rule's brush and named
  on the divider by the server.
- [youndie/kompot#205](https://github.com/youndie/kompot/issues/205) — the DSL cannot set `heading`
  or `accessibilityLabel`. The receipt and promo titles are built as `TextComponent(…, heading = true)`
  through `addComponent`.

**Accessibility of this product's own components.** kompot's renderers take their roles from the
wire; `TripRow`, `FareBreakdown` and `EarningsTile` are drawn here, so their renderers merge what
belongs together — a row, a fare line, a tile — into one stop. Nothing on screen changes, so no golden
can say it; `RendererSemanticsTest` does, and fails if a merge is removed.

**Impressions:** nothing to change. Nothing here calls `withImpressionTracking`.

**Goldens:** `screens_rider_receipt` and `screens_rider_receipt_light` re-recorded, for the rule;
every other golden verified unchanged against 0.39.
