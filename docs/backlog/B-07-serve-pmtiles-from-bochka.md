---
id: B-07
title: "Serve the pmtiles archive out of bochka and measure ranged reads"
status: open
priority: P2
size: S
stage: stage-1-skeleton
blocked_by: [B-06]
---

# B-07 — Serve the pmtiles archive out of bochka and measure ranged reads

Research §1.6 confirmed bochka serves `Range`, which is the requirement on paper. Browser tile
traffic is many small ranged reads against one large object, and that is not the shape bochka's
published measurements cover.

- **Measure with the real archive.** A number from a different load is not evidence about this one.
  [B-06](B-06-city-extract-and-tiles.md) built it: 16.6 MiB, 810 tiles, biggest tile 124 kB gzipped,
  and 1.2 MB of glyph PBFs in 512 small files beside it — which is a second load shape, many tiny
  objects rather than ranges into one big one.
- The rejected alternative is assuming it holds because the header is implemented.
- The fallback if it does not: a static file served beside the app. That costs the demo one talking
  point and nothing else, and knowing it early is what makes the risk cheap.

- AC: the archive served from bochka, a client fetching a city at zoom 10–16, with request count and
  latency distribution recorded beside what was measured.
- AC: the result written into research §3 Risk 6.
- Anchors: `bochka/README.md`, `bochka/bochka-http`
