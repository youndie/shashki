---
id: B-07
title: "Serve the pmtiles archive out of bochka and measure ranged reads"
status: done
priority: P2
size: S
stage: stage-1-skeleton
---


**Unblocked:** [B-06](B-06-city-extract-and-tiles.md) is done and the archive exists — 16.6 MiB,
810 tiles — so this item now has the real file its measurement is about.
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

- ~~AC: the archive served from bochka, a client fetching a city at zoom 10–16, with request count
  and latency distribution recorded beside what was measured.~~ **Done, 2026-09-02.** 812 requests
  for the whole archive — every tile in it, which is above zoom 16 and therefore covers 10–16 —
  p50 0.85–1.13 ms, p99 1.77–3.20 ms, 740–1 103 ms of request time across three runs. The glyph load
  measured beside it: 512 whole objects, p50 0.75 ms, 453 ms.
- ~~AC: the result written into research §3 Risk 6.~~ **Done: closed, with the table and with what
  the numbers do not cover.**
- ~~AC: the bochka coordinate this adds is a release or a CI-numbered publish, not a `-SNAPSHOT`~~
  **— no coordinate was added, and that is the answer rather than an evasion.** shashki links nothing
  from bochka: the browser fetches tiles over ordinary HTTP from wherever they are hosted, so bochka
  is the host and not a library. What is pinned is the *image*, `ghcr.io/youndie/bochka:v0.5.0` — a
  release tag, and the newest one published; the working copy's `0.6.0-SNAPSHOT` is unreleased and
  unused here. The clean-checkout guarantee from [B-13](B-13-pin-every-dependency.md) is therefore
  untouched, because the dependency graph did not change.
- Anchors: `bochka/README.md`, `bochka/bochka-http`

## What it turned out to be

**The numbers were never in doubt once measured, and the finding is not a number.**

Ranges into one 16.6 MiB object came back at p50 0.85–1.13 ms and p99 under 3.2 ms; whole reads of
512 small glyph objects came back at p50 0.75 ms. The two load shapes are within a factor of the
same, which is what the item asked and what it doubted. Three runs, and the first run's 27.67 ms
outlier is a cold start, named rather than averaged away.

**What actually mattered is that a browser cannot sign a request, and two switches have to be on.**
With `BOCHKA_ANONYMOUS=1` alone an unsigned `GET` is 403. It becomes 206 after a public-read bucket
policy. And without a CORS configuration the preflight is 403 *and* a plain `GET` carrying `Origin`
comes back with no `Access-Control-Allow-Origin` at all — so the browser refuses a response `curl`
accepts happily. `Range` is not on the CORS safelist, so every tile read is preflighted. A deployment
that set the policy and forgot the CORS rule would pass every command-line check and fail in the
product. That is in research §3 Risk 6 with the four measurements behind it.

**No dependency was added.** The item inherited a pinning criterion from B-13 on the assumption that
serving tiles from bochka means depending on bochka. It does not: the browser fetches over HTTP from
wherever the archive lives, so what is pinned is the image tag. Saying that plainly is better than
adding a dependency to satisfy a criterion.

**The client is committed** (`map/tile_serving.py`) for the same reason `label_coverage.py` is: a
measurement nobody can repeat is a number in prose. It signs SigV4 on the standard library rather
than pulling in boto3, because a retrying, pooling client between the measurement and the thing being
measured is exactly what should not be there — and it carries `upload`, `publish`, `cors`, `measure`
and `many`, which together are the deployment recipe as much as the measurement.
