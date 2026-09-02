---
id: B-66
title: "R4 asks the server for a quote once and never again"
status: open
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-66 — R4 asks the server for a quote once and never again

Open the rider before any driver is online and the class picker says *no cars nearby* on all three
rows — correctly. Bring a driver online and the screen does not change. Measured on the desktop
client against the stand: `POST /api/quotes` appears **zero** times in sixty seconds of server log
after the first load, tapping a tile does nothing, and the only cure is restarting the application,
after which the same screen shows `economy · 0 min · $ 28.96` and an order bar that works.

- **The wait is a fact about the world and the screen treats it as a fact about the request.** A
  quote's *price* is arithmetic and does not go stale; `pickupEtaSeconds` is "who is near right now",
  and a rider looking at a screen for two minutes is looking at a two-minute-old answer. The two
  arrive in one response, which is what makes it easy to cache both by accident.
- **A poll, at the cascade's own rhythm.** The driver's board is polled every two seconds and the
  reason is written down; this is the same shape for the same reason, and R4 is the screen a rider
  sits on longest. Something slower than the board and faster than a shift — the item that does it
  measures rather than guesses.
- The rejected alternative is a socket. `endpoint-driver` already argues it: a second connection and
  a second reconnect policy for a message that changes every few seconds is the wrong trade, and this
  one changes less often than the driver's own position.
- Deliberately **not** covered: refreshing the *route*. The road from A to B does not move, and
  re-asking for it on a timer would be a graph search per rider per tick.

- AC: with the picker open, a driver coming online turns the tiles from `no cars nearby` into a price
  and an ETA without the application being restarted.
- AC: the poll stops when the screen is not on top of the stack — a class picker underneath a trip is
  not asking anybody anything.
- Anchors: `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/ride/ui/ClassPickerViewModel.kt`,
  `docs/screens/screen-rider-class-picker.md`
