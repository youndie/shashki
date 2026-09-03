---
id: B-74
title: "D3's fare is white where the kit's is amber, and two of its lines are empty"
status: open
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-74 — D3's fare is white where the kit's is amber, and two of its lines are empty

Live D3 is close to the kit's OfferCard — fare and countdown at 54, the draining bar, decline as a
bare circle and *accept* the only filled surface — and differs in three places the kit is explicit
about.

- **The fare is set in the accent** (`420 ₽`, amber) and here it is white. On a windscreen mount the
  amber figure is the thing the two-second read is built around.
- **The pickup line has a dash where the kit has `2.1 km · 4 min from you`.** The offer carries the
  ride's distance and duration and not the driver's distance to the pickup; the driver's own
  position is on the socket and the road between the two is one route request.
- **There is no passenger line** (`Anna · 4.98`). The protocol has no rider rating, so this is a wire
  gap rather than a screen one, and the item says so rather than inventing a number.
- Deliberately **not** covered: the pivot header above the card (`driver-1 / economy / documents`),
  which the kit's D3 suppresses — "the offer screen may suppress the app bar entirely" is an open
  question in the kit itself.

- AC: the fare on the offer card is drawn in the driver's accent; the pickup line carries the
  driver's distance and time to it.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/OfferCard.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/`
