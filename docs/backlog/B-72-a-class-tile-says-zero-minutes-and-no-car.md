---
id: B-72
title: "A class tile says 0 min and names no car"
status: open
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
