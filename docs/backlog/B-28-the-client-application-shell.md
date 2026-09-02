---
id: B-28
title: "A client application exists to put the screens in"
status: done
priority: P1
size: L
stage: stage-3-surface
---

# B-28 — A client application exists to put the screens in

**Filed 2026-09-02 by finishing everything else.** Every other item in this backlog is closed, and
three of them ended the same way: the work was done, the acceptance was met as far as it could be,
and what was left over needed *an application* — something with an entry point, a navigation stack
and a composition root — which no item ever scheduled.

- [B-09](B-09-browser-side-pkce.md) built the PKCE client and could not sign anybody in.
- [B-10](B-10-crash-reports-from-the-browser.md) built the crash reporter and its browser hook
  compiles and runs nowhere, because nothing loads a page.
- [B-25](B-25-rider-trip-in-progress.md) built the trip screen; it exists only as a golden.

That is not three oversights. The backlog was written to retire the unknowns and prove the seams, and
it did: the saga survives the process dying, the offer expires on a deadline, the map draws the city
from its own tiles, the receipt goes out over verified TLS, the crash arrives in katcher. **What it
never contained was the shell those parts hang in**, and the shape of that shell was undecidable
until [D1](../research/research-architecture.md#d1-the-browser-is-a-decision-with-a-date-not-a-precondition)
chose a target. It is decidable now.

- **This is a decision to take, not a task to start.** It is `L`, it is the first item that is
  ordinary product work rather than a question with an answer, and how much of it is worth building
  depends on what the demo is for. Written down so the state of the plan is legible.
- **Two bundles**, by [D10](../research/research-architecture.md#d10-two-bundles-and-the-number-is-that-the-roles-are-5--of-one).
- Navigation is Navigation 3 with `@Serializable` routes, and in a browser the back button and the
  address bar are part of the interface — the client skill says so and a wasm target makes it true
  rather than aspirational.
- The pieces waiting for it are built and tested: `MapSurface` and `CanvasMapSurface`, `RiderTheme`,
  `RiderClassPicker`, `RiderTripInProgress`, `SignInAttempt`, `CrashReporter`, the kompot renderers.

- ~~AC: a rider bundle that starts, asks for a car and watches it arrive, against the server this
  repository already has.~~ **Done, 2026-09-02.** `:rider` builds a Kotlin/Wasm bundle; the class
  picker prices the journey through `POST /api/quotes`, ordering goes to `POST /api/rides`, and the
  trip screen polls the ride and the car and draws both. Fifteen tests cover it against a
  hand-written fake server, and two goldens photograph the application's own mapping.
  **"Signs in" is not done and belongs to [B-26](B-26-sign-in-end-to-end.md)**, which exists for it
  and is blocked on a running shildik rather than on this.
- ~~AC: the browser's back button and address bar work, because in a browser they are the
  interface.~~ **Done.** `AddressBar` is a port: `history.pushState` out, `popstate` in, and both
  directions — a handler that only popped would break forward silently. `RiderRouteTest` holds the
  path mapping to a round trip and requires an unknown address to be nothing rather than a crash.
- ~~AC: `installCrashReporting` is called, so B-10's hook stops being code nobody runs.~~ **Done**,
  inside the Koin scope so the reporter shares the application's HTTP client, and behind a
  `CrashReporting` wrapper because a Koin `single` that returned `null` would fail at injection with
  a message about the type rather than about the configuration. `RiderGraphTest` asserts both halves.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`,
  `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `crash-client/src/commonMain/kotlin/io/github/youndie/shashki/crash/CrashReporter.kt`

## What it turned out to be

**The first thing the shell did was find two questions the server could not answer.**

Every screen in `:shared-ui` was built from values and photographed that way, and both were correct —
and neither could show that the values had nowhere to come from. R4 draws three prices before
anything is ordered and the server had no way to price a journey without creating a ride; the trip
screen draws a car and `RideView` carries a `driverId` and nothing about where that driver is. So
this item added `POST /api/quotes` and `GET /api/rides/{id}/driver`, forty lines each, plus a
`whereIs` on the driver index to answer the second.

That is the argument for the shell being an item rather than a formality, and it is in research
§1.7d. A golden is a picture of a screen fed values: a screen whose values do not exist photographs
exactly as well as one whose values do.

**A third question is still unanswered, and the screen says so.** The kit's class tile reads
"4 min · Kia Rio" — the wait for a car and which car. The wait is a route from the nearest candidate
driver to the pickup, a query the server does not expose; the car belongs to a driver nobody has
assigned yet. Both are drawn as a dash, on the same rule as the trip screen's blank registration: a
number in the wrong place reads as an answer, a dash reads as a question. The first golden of this
screen had the *journey* duration in that row — the right number in the wrong place — and it looked
completely convincing.

**D10's one unmeasured assertion is now measured.** It said two bundles would share the runtime *if*
the content-hashed skiko file came out identical, and marked that as a hope with a mechanism. There
are two bundles now: same name, same 8 640 316 bytes, same sha256, from independent webpack runs.
The rider's own half is 1 087 188 gzipped bytes of wasm and 100 128 of JavaScript.

**Three things about the toolkits, each of which cost a build cycle.**

- Navigation 3's runtime half is `androidx.navigation3`, published by Google and **not on Maven
  Central**; the UI half is JetBrains' port. Both are alphas and they are the only line published for
  `wasmJs`. The repository is added filtered, like the others.
- A constructor parameter shadows a property of the same name inside `init`, so
  `private val scope = scope ?: viewModelScope` silently referred to the nullable parameter.
- `runTest` drains its scheduler before finishing, and `WatchDriverUseCase` polls for as long as the
  screen is open — correct behaviour that makes `advanceUntilIdle` run virtual time for ever. The
  loops' scope is a constructor parameter for that reason, and a test passes its `backgroundScope`.

**And a third copy of eleven lines.** `portable()` — the ramp with hinting pinned — now exists in
kvadrant-ui, in `:shared-ui` and here, each copy `internal` and each forced: a screenshot helper
cannot live in `commonMain` without making a UI library depend on a screenshot library at run time,
and Kotlin Multiplatform has no `testFixtures` to publish it from. Recorded where the third copy is,
not fixed: the shape that ends it is a small published testing module, and that is a decision about
the portfolio.

**What is deliberately not here.** The driver bundle — D10's second, which nothing schedules yet. The
tile transport, so the map draws the style's background and everything the server said on top of it,
in the right place, and no streets (§1.8b). The pickup ETA above. Sign-in, which is B-26.
