---
id: B-21
title: "The ramp projection is checked against the stock components that read it, by golden"
status: open
priority: P0
size: S
stage: stage-0-unknowns
---

# B-21 — The ramp projection is checked against the stock components that read it, by golden

`ShashkiTypography.toKvadrant` maps the kit's seven styles onto the library's slots by size —
`title = rowEmphasis` because both are 17 sp, `mediumLarge = tileLabel` because both are 19. But a
slot is what a library *component* reads, not a size, and which components read which slot was not
checked. If `KvadrantListItem` draws its primary line from `title`, every stock list row in the
product renders at 17 / W400 where the kit's list title is 15 / W400 — the row R3 draws, and the row
every history and suggestion list is made of. Research §1.1 records this as the `pageTitle` trap one
slot over: the projection assumed each slot's usage is the kit's usage of that size.

- **Settle it with a golden, not by reading the library.** One fixture renders every stock component
  shashki will actually use — `KvadrantListItem`, `KvadrantButton`, `KvadrantTextBox`, a pivot
  header, a tile — under `RiderTheme`, laid out as the kit's R3 and R9 rows, so the diff against the
  artboard is readable. Reading the source says what a component does today; the golden says it on
  every version B-13 pins.
- **Where the projection is wrong, the fix is in `toKvadrant`, not at call sites.** A slot that the
  kit uses two ways is the signal to stop projecting it and draw that component with
  `ShashkiTheme.typography` directly — one more line in the projection's KDoc saying why.
- The rejected alternative is discovering it in B-04, where the first screen made of list rows
  comes out subtly wrong and the fix competes with building the two components.
- Not covered: the panorama slots. The product draws no panorama and the projection leaves them as
  the library built them.

- AC: `foundation_stock_components` golden exists in both themes, and its list row measures 15 sp
  against the kit's R3 — or the projection's KDoc says which slot was withdrawn and why.
- AC: the fixture strings go through the glyph-coverage guard ([B-05](B-05-glyph-coverage-guard.md))
  once it exists; until then they are drawn from the strings the type-ramp fixture already proved.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/ShashkiTypography.kt`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/SkeletonFixtures.kt`,
  `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/`
