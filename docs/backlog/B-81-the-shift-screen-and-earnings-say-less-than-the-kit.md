---
id: B-81
title: "D2 is a word and a button, and D6 is three sums: what the kit's tiles say that these do not"
status: open
priority: P3
size: M
stage: stage-6-what-running-it-said
---

# B-81 — D2 is a word and a button, and D6 is three sums: what the kit's tiles say that these do not

The kit's D2 is the driver's map with the online toggle and four tiles — `online 7:12 h`, `today
4 280 ₽ · 11 trips`, `rating 4.9`, `acceptance 96 %`; offline swaps the accent tile for chrome and
dims the map. The kit's D6 is `this week 26 940 ₽ / trips 68 · avg 396 ₽ / online 41:20 hours` and a
payouts list `28 aug · 11 trips · card · 4 280 ₽`.

Live D2 is `offline` in the disabled brush and *go online*; online, `waiting / 118 positions taken`.
Live D6 is `$ 46.32` three times — today, week, all time — with the *history* item empty.

- **D2's shape is a decision and is written down**: `screen-driver-shift.md` says "three states and
  one screen, because that is how a shift feels". The tiles are not; the numbers behind them exist
  (payouts, the socket's uptime, the offers answered) and `EarningsTile` is the registered component
  for exactly this.
- **D6's three identical sums are the stand's, not the screen's** — one driver, one day — but the
  trips count and the payouts list are the part of D6 that says anything, and neither is drawn.
- Deliberately **not** covered: the map on D2. The driver's own position is a dot on a map they are
  sitting in; B-75 is where the map earns its place.

- AC: D2 online shows hours online, today's takings with the trip count, and acceptance, as tiles;
  D6 shows the count and average beside each sum and the payouts as rows under *history*.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverShift.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverEarnings.kt`,
  `docs/screens/screen-driver-shift.md`
