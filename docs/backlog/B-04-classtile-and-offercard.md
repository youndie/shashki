---
id: B-04
title: "ClassTile and OfferCard on kvadrant primitives"
status: open
priority: P0
size: M
stage: stage-0-unknowns
blocked_by: [B-03, B-21]
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

- AC: `ClassTile` fixtures for selected / default / unavailable, and the selected one has black ink.
- AC: `OfferCard` at 15…01, `tabular-nums`, no map behind it, full bleed, and the digits do not
  shift width between frames.
- AC: both render at 390 × 844 and their goldens are in the suite.
- Anchors: `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/KvadrantTile.kt`
