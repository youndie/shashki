---
id: B-72
title: "A class tile says 0 min and names no car"
status: done
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-72 — A class tile says 0 min and names no car

The kit's R4 tile is `economy / 4 min · Kia Rio / 249 ₽`. Live, the selected tile reads
`economy / 0 min / $ 28.96`: the one driver on the stand is parked at the pickup, the ETA is nought
seconds, and `asDuration()` — whose own note says a rider told "0 min" stops believing it — rounds
up only from one second.

- **"0 min" is a stand artefact and a real sentence.** A car at the kerb is a real case, and the kit
  would say *here* or *now*, not a number.
- **No car on the tile is a wire gap.** `ClassOffer` carries the wait and the price; the driver record
  (B-63) carries `Skoda Octavia · white`, and it is what R6 shows — the tile could name the nearest
  candidate's car the same way, or say nothing, but not leave the slot that the kit fills empty.
- Deliberately **not** covered: the ETA arithmetic itself, which is `PickupEta`'s and measured.

- AC: an ETA under a minute reads as the kit would say it, and the tile shows the car the wait was
  computed for when the candidate is known.
- Anchors: `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/format/Formatting.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/ClassPickerScreen.kt`

## What it turned out to be

**Two small things and one refusal.** `Int.asWait()` says *here* under thirty seconds and the
rounded-up minutes past them, so the stand's driver parked at the pickup no longer produces `0 min`
— a number nobody would say. `ClassQuote.car` carries the driver record's own string for the nearest
candidate, and the tile joins the two: `here · Skoda Octavia · white`, the kit's `4 min · Kia Rio`
with this product's names in it.

**The wait names its driver now.** `PickupEta.waitFor` returns the seconds *and* the candidate they
were routed for, and the quote route looks that driver up; it used to return the seconds alone, which
was the whole reason the car could not be on the tile. A candidate with no record gets the wait
alone — the refusal: a model guessed from the class would be fiction on the field a rider checks a
real car against.

`PickupEtaTest` asserts the car off the wire for the harness's seeded driver and its absence when
nobody is online; `ClassOfferTest` holds the three forms of the meta line. The goldens did not move:
the fixtures already said `4 min · Kia Rio`, which is what this makes true.
