---
id: B-73
title: "R5 says less than the kit's matching screen: no count, no clock, no class and price"
status: open
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-73 — R5 says less than the kit's matching screen: no count, no clock, no class and price

Live R5 is `looking for a car / asking the drivers around you / · · · / airport · 22.8 km · 35 min /
cancel`. The kit's R5 is `looking for / a driver / 14 cars within 3 km / asking the closest first ·
0:24 / economy · 249 ₽ / Lenina st, 14 → Airport, terminal B / cancel search`.

- The count and the clock are what make a wait bearable, and both are known: the cascade knows how
  many candidates it has and how long the current offer has left (B-43's `OfferStep`).
- The class and the price were chosen one screen ago and are what the rider is waiting *for*.
- Deliberately **not** covered: the 4.4 s progress-dot cycle, which is the kit's motion and is
  drawn already.

- AC: R5 shows how many cars were asked, how long the current ask has left, and the class and price
  the rider chose.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderMatching.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/MatchingViewModel.kt`
