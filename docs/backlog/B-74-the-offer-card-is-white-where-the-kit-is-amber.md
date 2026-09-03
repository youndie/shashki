---
id: B-74
title: "D3's fare is white where the kit's is amber, and two of its lines are empty"
status: done
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

- ~~AC: the fare on the offer card is drawn in the driver's accent.~~ **Withdrawn, 2026-09-03**, and
  the item's premise was wrong here: the white fare is a recorded decision, not a drift. B-48 put the
  kit's amber on the light theme and measured **2.11:1 on white** — the worst number in the palette,
  on the one figure a driver has fifteen seconds to read — and the rule that came out of it stands:
  accent-coloured *text* is a control's label, figures take the foreground, and the accent leads this
  card as *surfaces* (the strip and the accept). The kit drew the card in dark only; this product has
  two themes and one card. `OfferCard.kt` says so in as many words.
- AC: the pickup line carries the driver's distance and time to it.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/OfferCard.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/`

## What it turned out to be

**One of the three was a decision and two were gaps; one gap is closed and one is named.**

- **The amber fare stays white**, and the item was wrong to call it a drift: B-48 measured the kit's
  amber at 2.11:1 on the light theme and ruled that figures take the foreground while the accent
  leads a card as surfaces. The kit drew D3 in dark only; this product has two themes and one card,
  and `OfferCard.kt` had said so since before the sweep. The AC is struck through above rather than
  deleted.
- **The pickup line is the driver's own road now.** `OfferView` carries `fromDriverMetres` and
  `fromDriverSeconds`, routed on the server from the position the driver's socket last reported to
  the pickup, at the moment the offer is read; the card says `2.1 km · 4 min from you` and keeps
  its dash for a driver the server has no position for or no road to. `RideRoutesTest` asserts the
  number off the wire.
- **The passenger line is still a wire gap.** There is no rider rating in the protocol, and a screen
  that showed one would be a second rating system. Recorded here rather than built.

The goldens did not move: the card's fixtures already carried the kit's own strings, which is what
this makes true on the stand.
