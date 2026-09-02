---
id: B-28
title: "A client application exists to put the screens in"
status: open
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

- AC: a rider bundle that starts, signs in, asks for a car and watches it arrive, against the server
  this repository already has.
- AC: the browser's back button and address bar work, because in a browser they are the interface.
- AC: `installCrashReporting` is called, so B-10's hook stops being code nobody runs.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`,
  `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `crash-client/src/commonMain/kotlin/io/github/youndie/shashki/crash/CrashReporter.kt`
