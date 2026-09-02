---
id: B-56
title: "An uncaught failure leaves a blank page and no words at all"
status: done
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-56 — An uncaught failure leaves a blank page and no words at all

Twice in one evening on the stand, a single unhandled exception took the whole application down to a
blank rectangle: a 404 on the tile archive (`expected 206 … got 404`) painted the rider black before
any panel appeared, and the refused token exchange ([B-55](B-55-browser-sign-in-needs-an-unreleased-shildik.md))
painted the driver white. In both cases Compose had already started — the console shows Koin and the
WebGL context — and the failure happened in a coroutine nobody was catching.

- **A reference product owes a reader a sentence.** [B-10](B-10-crash-reports-from-the-browser.md)
  sends the throw to katcher, which is the operator's half; the person looking at the screen gets
  nothing. One top-level boundary that draws the kit's state typography — a headline, one line of
  what failed, and a reload — costs a screen and turns "it does not work" into "the tiles are not
  where I said they were".
- **The map's absence in particular must not be fatal.** The basemap is a background; R2 and R4 have
  a panel, a price and an order button that are all perfectly usable over black. A tile archive that
  404s should degrade to no map, and say so, rather than take the page.
- The rejected alternative is catching per call site. The two failures came from different layers —
  a tile reader and the auth client — and the thing they have in common is being unhandled, not
  being related.
- Deliberately **not** covered: retry policy. The screen says what happened and offers a reload;
  deciding which failures are transient is a separate question.

- AC: with `SHASHKI_TILES_URL` pointing at an object that does not exist, the rider still draws the
  class picker and says the map is unavailable.
- AC: an exception escaping the composition draws a state screen naming it, and still reaches
  katcher.
- Anchors: `rider/src/wasmJsMain/kotlin/io/github/youndie/shashki/rider/Main.kt`,
  `driver/src/wasmJsMain/kotlin/io/github/youndie/shashki/driver/Main.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`

## What it turned out to be

**Two mechanisms, because the two failures had nothing in common except being unhandled.**

**The map degrades now, which its own type had claimed all along.** `PmtilesTileSource`'s note says
the map is the one part of this product that can be missing without the screen being wrong; the code
threw the failure out of a `LaunchedEffect`, which takes the composition with it. It now records the
failure, refuses to go back to an archive it knows is not there — four tiles a frame, for as long as
the screen is open, is what "retry" would have meant here — and `CanvasMapSurface` draws
`no map: <what failed>` in the subtle brush. `CancellationException` is re-thrown rather than
recorded: a navigation is not a missing archive.

**And there is a band, in the DOM.** A composition that has thrown cannot draw its own apology, so
the fallback is plain markup installed before the application starts and independent of it — the
kit's own rule for a message arriving over something else (R7·a: "a full-width band, never a floating
card"). It is installed *beside* `installCrashReporting` rather than inside it, so neither can stop
the other and the order they fire in does not matter.

**Verified where it broke.** With the tiles bucket empty, the rider draws the whole class picker —
title, three classes, the card row, the order bar — and says `no map: Fail to fetch` where the map
would be. That is the same configuration that produced a black page at the start of the evening. The
band is asserted in a real Chrome by `FatalBandTest`: a synchronous throw and a rejected promise each
put it on the page carrying their own message, a second failure replaces the first rather than
stacking, and removing the `error` listener makes all three fail.

**What is deliberately still true**: the band appears for any uncaught failure, not only a fatal one.
A rejection the application survives will show it over a working screen, which is the trade the kit's
band shape was chosen for — a message about something that went wrong is not a lie just because the
screen underneath still works.
