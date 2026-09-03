---
id: B-73
title: "R5 says less than the kit's matching screen: no count, no clock, no class and price"
status: done
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

## What it turned out to be

**Three numbers the server already had and had not been sending, and one it had not been keeping.**
The cascade knew how many candidates the index answered with, which attempt was out and when it
expired — `Enriched.OFFER_ATTEMPT` and `OFFER_EXPIRES_AT` were on the saga row since B-43 — but
nothing read them back into the ride. `RideView.search` does now, at `MATCHING` and nowhere else:
`carsNearby`, `asked`, the deadline **and the server's clock beside it**, so the screen counts down a
duration it was handed — `OfferView`'s rule, borrowed rather than rediscovered.

**The count is the one the cascade started with.** The index answers the question afresh on every
attempt, and a number that shrank while the rider watched would read as cars leaving rather than as
the cascade moving down its list; `OFFER_CANDIDATES` is written on the first offer and carried
through the rest.

**The countdown restarts when the offer changes and not on every poll.** The first cut restarted on
every answer and the test caught it — with a fake whose clock does not move, the poll reset the
fifteen seconds every two, and three seconds in the clock read fourteen. The fix is the right
behaviour anyway: a poll that carries the same deadline again is not news, and a late answer that
reset the clock would make it jump.

R5 now reads `looking for a car / 3 cars nearby / asking the closest first · 0:12 / ⋯ / economy ·
$ 28.96 / airport · 22.8 km · 35 min / cancel`, which is the kit's screen with this product's
numbers in it. Four goldens re-recorded — R5 and R10 on both themes — and looked at.
