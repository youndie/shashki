---
id: B-54
title: "The shift's count rises for frames the server threw away"
status: done
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

## What it turned out to be

**The server had nothing to say, and that was the whole gap.** `driverPositionRoutes` read frames
and answered none, so the only thing the client could possibly count was its own `send` returning —
which means the bytes left this process and nothing more. The fix is one line on each side: the
server echoes a report it has put in the index and stays silent about one it drops, and
`WebSocketShiftRepository` splits into a sender and a reader, emitting what came **back**.

**The report itself is the acknowledgement, rather than a new type.** `ShiftRepository.stream` is
already `Flow<DriverReport>` on both sides, so nothing on the wire had to be invented, and a shift
sends one frame every four seconds — the doubled bytes are not a question anybody will ask.

**The screen's word changed with its meaning.** `42 positions sent` is now `42 positions taken`:
*sent* was true of the old number and would have been a lie about the new one, and the difference
between them is exactly what a driver needs when the server is refusing everything.

**Two guards, and each fails without its half.** On the client, `FakeShiftRepository.accepting =
false` is a server that takes nothing — the count stays at zero while three frames go out, and the
control is that same test against the old code. On the server, `ProtectedDriverRoutesTest` now sends
two frames, one for somebody else, and asserts **one** acknowledgement comes back; removing the
mismatch check makes a second arrive and the test fails.

**What it does not fix is [B-64](B-64-the-offer-reaches-the-client-and-not-the-screen.md).** The
frozen count observed there was read as "the socket stalled"; with an honest count that reading can
be made again and will mean something. The offer card is still not drawn.
