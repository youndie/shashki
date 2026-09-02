---
id: B-54
title: "The shift's count rises for frames the server threw away"
status: open
priority: P0
size: S
stage: stage-6-what-running-it-said
blocked_by: [B-53]
---

# B-54 — The shift's count rises for frames the server threw away

`DriverShift`'s KDoc says the count "rises each time the socket actually took a position, so a driver
who is 'online' over a socket that quietly died sees a number that has stopped". It does not do that.
`WebSocketShiftRepository.stream` emits the report immediately after `send(Frame.Text(...))` returns,
which means the frame was written to the local socket and nothing more. On the stand, with
[B-53](B-53-the-driver-bundle-cannot-go-online.md) live, the server discarded **every** frame and the
screen read `waiting · 3 positions sent` — the exact failure the number was introduced to make
visible, hidden by the number.

- **A count of what was sent is a count of the client's own intentions.** The screen already has one
  of those: the word *waiting*. What it owes a driver is the server's side of the sentence, so the
  count has to come back from the server rather than from `send` returning.
- **The server has to say something.** Today `driverPositionRoutes` reads frames and answers nothing;
  the cheapest honest change is one frame back per accepted report — the same `DriverReport`, or a
  bare acknowledgement — and the client counts what arrives. A dropped frame then leaves the number
  where it was, which is what the screen promises.
- The rejected alternative is polling the index for "am I on the map": a second mechanism for a fact
  the socket already knows, and one that answers about staleness rather than about this frame.
- Deliberately **not** covered: showing the driver *why* a frame was refused. `DroppedFrames` counts
  them for a graph and that is where a mismatch belongs; the screen's job is to stop claiming
  success.

- AC: with the client sending frames the server refuses, the count does not rise and the screen does
  not say *waiting* over a socket that is achieving nothing.
- AC: a test asserts the count follows the server's acknowledgement, not the local `send` — the
  control is the same test against today's code.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/data/WebSocketShiftRepository.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/DriverPositionRouting.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverShift.kt`
