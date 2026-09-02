---
id: B-56
title: "An uncaught failure leaves a blank page and no words at all"
status: open
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
