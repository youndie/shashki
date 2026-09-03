---
id: B-78
title: "R9's rows wrap unevenly, a cancelled ride says — for 0, and the pivot header is clipped"
status: done
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-78 — R9's rows wrap unevenly, a cancelled ride says — for 0, and the pivot header is clipped

Three things on the live R9, all layout.

- **The row joins both ends with an em dash and wraps.** `46.0511, 14.5051 — 46.2237, 14.4576` fits
  on one line beside `—` and wraps to two beside `$ 28.96`, so rows of the same list have two
  heights. The kit's TripRow leads with a **route stack** — two addresses one over the other, with
  the pickup and drop-off glyphs — and the amount on the right never changes the stack's shape. The
  kit's rule 4, *rows own their leading slot*, is this row's.
- **A cancelled ride shows `—`; the kit shows `0 ₽`.** A dash is "no money moved", which is true and
  is the same glyph the class picker uses for "no cars"; the kit's zero says the ride was charged
  nothing, which is what a rider wants to read.
- **The pivot header is clipped at the window's edge** (`trips profile pro…`). The kit's headers
  "travel at their own rate and wrap round"; the desktop window is exactly 390 dp and the third item
  is cut, on the rider's R9 and the driver's D6 alike. Whether `KvadrantPivot` scrolls its header is
  the kit's question; this item finds out and records the answer.

- AC: a row is a route stack with the amount on the right, and rows are one height; a cancelled
  ride reads `$ 0`.
- ~~AC: the pivot header is not clipped at 390 dp.~~ **Withdrawn**: the cut header is the kit's own
  motion — Metro's pivot shows the next item peeking past the edge and wraps round as the pages
  turn. The sweep read the library doing what the kit says as a defect.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/Renderers.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderHistory.kt`

## What it turned out to be

**Two of three were real, and the third was the kit.**

- **The row is a route stack now.** `TripRow.from` and `to` carry both ends; the renderer leads
  with two lines, each with its pin, and the amount on the right no longer changes the stack's
  shape — rule 4, *a row leads with a route stack, one glyph, or nothing*. `title` stays for a row
  with one end or none, and for a consumer that draws no stacks. Every row of R9 is one height
  again, which the em dash that wrapped beside a wide amount had broken.
- **A cancelled ride says `$ 0`.** The dash was "no money moved", and it is what the kit's row
  says for a ride still running; a cancelled one is a settlement that took nothing, and the kit
  says the zero. `HistoryViewModelTest` holds both.
- **The pivot's third header being cut at the edge is the kit's own peek**, not clipping. The AC
  is struck through above, and the screen document says so where the next reader meets it.

**And the payment moved under the amount.** With the stack in, the meta line — date, time, class,
card — was the thing that wrapped beside a wide amount; the kit's right column is `470 ₽ / card`,
and `TripRow.note` draws it there. Every line of a row is one line now. Two goldens re-recorded —
R9 on both themes — and looked at.
