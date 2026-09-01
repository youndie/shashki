package io.github.youndie.shashki.ui

import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.theme.KvadrantMetrics

/**
 * The kit's measurements, as drawn.
 *
 * **Every spacing number here is exactly 4/3 of the library's, and the evidence says the library's
 * is the authentic one.** 4/3 is 1 / 0.75 — the kit's own stated px → dp factor — and each value is
 * the raw pixel number in Metro's token dump: page margin 12 px, tile gap 12 px. The kit's *type*
 * ramp, by contrast, matches the converted column exactly. A deliberate scale-up that lands on the
 * pixel column for spacing while leaving type on the dp column is not a story that holds together;
 * a skipped conversion is.
 *
 * **It is shipped anyway, and that was decided rather than overlooked** (B-15). The kit is this
 * product's design authority, its artboards are what the goldens are diffed against, and the look
 * was approved at these numbers. Metro fidelity is kvadrant-ui's job. Do not "fix" this back to the
 * library's defaults without changing the kit first — research §1.1c is the argument.
 *
 * **`scale` stays at 1f**, which is not a detail: `KvadrantTheme` multiplies the type ramp by it, so
 * a scaled metric set would drag [ShashkiTypography] with it and put the 54 sp page title at 61.6 or
 * 72 sp — a size the kit's own invariant forbids. The numbers are stated, never fitted.
 */
public fun shashkiMetrics(): KvadrantMetrics =
    KvadrantMetrics(
        margin = MARGIN,
        tileGap = GAP,
        // **Derived from the canvas rather than scaled**, because the library's tile sizes are fixed
        // and the kit's grid is fitted: "4 col tile grid at 390". `KvadrantTile` reads these three
        // directly — Small is square at `tileSmall`, Medium square at `tileMedium`, Wide is
        // `tileWide` by `tileMedium` — so getting them wrong is a layout that silently does not span
        // the page.
        //
        //   content   = 390 − 2 × 12          = 366
        //   small     = (366 − 3 × 12) / 4    = 82.5
        //   medium    = 2 × 82.5 + 12         = 177
        //   wide      = 4 × 82.5 + 3 × 12     = 366
        //
        // Wide comes out 366 × 177, which is 2.07 : 1 — the same proportion the library's own
        // 324 × 157.5 has, so the kit's "wide is 2:1, the others square" survives the refit.
        tileSmall = 82.5.dp,
        tileMedium = 177.dp,
        tileWide = CANVAS - MARGIN * 2,
    )

/** The kit's base canvas. Every tile size above is solved against it. */
private val CANVAS = 390.dp
private val MARGIN = 12.dp
private val GAP = 12.dp

// **The app bar's five fields are deliberately left at the library's values**, and this comment is
// the record of why rather than an omission.
//
// The kit gives them as "48 dp circle · 1.5 dp ring · 26 dp glyph box" against the library's 36 /
// 1.125 / 19.5 — the same 4/3 as everything else, so "as drawn" would say to take them. But 48 dp is
// also `KvadrantMetrics.touchTargetMin`, the modern minimum the library already enforces *around* a
// 36 dp visual, so a kit row reading "48 dp circle" may be naming the target or the ring, and those
// are different pictures. Nothing in this product draws an app bar yet.
//
// **Settled by B-04's OfferCard, and the library's values stand.** The kit's own markup draws the
// decline ring at `48 × scale(.75)` — a 36 dp visual inside a 48 dp target — which is exactly what
// `KvadrantAppBarButton` already does. The kit's "48 dp circle" in section 04 was naming the touch
// target. So the five `appBar*` fields keep the library's numbers, and `OfferCard` reads them.
