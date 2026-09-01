---
id: B-04
title: "ClassTile and OfferCard on kvadrant primitives"
status: done
priority: P0
size: M
stage: stage-0-unknowns
---

# B-04 — ClassTile and OfferCard on kvadrant primitives

These two carry the whole of research §1.1's divergence between the kit and the library: an accent
fill with black ink on it, a 54 / W200 figure that exists in no stock slot, a countdown in tabular
numerals, and a `wide` tile on the four-column grid whose unavailable state replaces a price with an
em dash. Everything else in the kit is transcription; if these come out right the rest follows.

- **Build them before the screens.** A component that is wrong is wrong once; a screen built on a
  wrong component is wrong everywhere.
- The rejected alternative is starting from the screen list. That produces eighteen half-checked
  copies of the same two decisions.
- Not covered: `TripRow`, `FareBreakdown`, `EarningsTile` — those are server-driven and wait on the
  kompot renderer.
- **Inherited from [B-21](B-21-ramp-projection-against-stock-components.md), as requirements:**
  the pivot header row ("trips · profile · promo") and every button are shashki composables drawing
  from `ShashkiTheme.typography` — `tileLabel` and `body` — not `KvadrantPivotHeaders` and
  `KvadrantButton`, which read Metro's slots at Metro's sizes (54 and an emboldened 19). The
  `OfferCard`'s accept is the first button; `RiderHistory`'s pivot is the first header row. The
  address field's placeholder size is the same question one component over and is decided here, by
  the artboard.

## What it turned out to be

Three goldens in `components/`, all at 390 × 844, all verifying on Linux under `--rerun-tasks`:
`class_tile`, `offer_card`, `offer_countdown`. Plus the first four of the handoff's twenty-four
icons — car ×3, the two pins, close — transcribed from the kit's SVG path data into `ShashkiIcons`,
tinted at the call site so one vector serves black-on-accent, white-on-chrome and the disabled brush.

- ~~AC: `ClassTile` fixtures for selected / default / unavailable, and the selected one has black
  ink.~~ Done. **Not a `KvadrantTile`**, and the artboard decided it: the kit's markup is a padded
  row and three of them fit under the address on R4, where a `TileSize.Wide` is 366 × 177 by the
  metric set and three would not. "Wide, 4 columns" means spans the columns, not is the 2:1 tile.
  Unavailable keeps the row's height with an em dash, everything in the disabled brush.
- ~~AC: `OfferCard` at 15…01, `tabular-nums`, no map behind it, full bleed, and the digits do not
  shift width between frames.~~ Done — and *shown*: `offer_countdown` stacks 15, 09 and 01, and
  their right edges align to the pixel. The bar is drawn at `secondsLeft / secondsTotal` rather than
  the kit's illustrative 64 %, because a golden showing the bar and the number disagreeing would
  record a bug as a reference.
- ~~AC: both render at 390 × 844 and their goldens are in the suite.~~ Done.

**Three places the kit disagrees with itself, resolved by the artboard and written into KDoc.**
The class name is 19 / **W400** in the ClassTile markup where the type table's `tileLabel` is
19 / W300. Accept is 17 / **SemiBold** with 0.02 em tracking where the table says "button 15 / 400";
it is the one filled surface on the screen and the one button in the product that is not `body` —
B-21's withdrawal holds in the way that matters, it is shashki's composable and not
`KvadrantButton`. And the "may an app bar carry a filled accept" question, answered *simplify*
(B-15), came out as: a plain 54 dp chrome row holding the library's `KvadrantAppBarButton` for the
ring and the accept drawn here — not `KvadrantAppBar` with its menu and mini state.

**The app bar's numbers, deferred from B-03, are now used as the library has them**: the strip is
`appBarHeight` 54, the ring is `KvadrantAppBarButton` at 36 with its 48 dp target, the glyph is
`appBarGlyph`. The kit's OfferCard markup draws the ring at `48 × scale(.75)` = 36 — so the kit's
"48 dp circle" in section 04 was the *touch target* after all, and the visual is the library's. One
ambiguity from §1.1c closed by a component that had to pick.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/KvadrantTile.kt`
