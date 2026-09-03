---
id: B-82
title: "Pressing a tile moves nothing: the kit's tilt was drawing inside the surface"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-82 — Pressing a tile moves nothing: the kit's tilt was drawing inside the surface

Found by pressing a class tile on the running desktop rider (2026-09-03): nothing moves. The kit's
foundation row names the press feedback beside the turnstile — **"tilt — press feedback is the
theme's"** — and kvadrant-ui's own tiles have it.

- **The obvious reading was wrong, and it is worth writing down because it cost the first hour.**
  "This product never took the modifier" — research §1.1 already records the opposite:
  `KvadrantTheme` provides `LocalIndication = TiltIndication(...)`, unconditionally, and every plain
  `Modifier.clickable` in this repository was therefore *already asking for the tilt*.
- **What it actually was: an indication draws what comes after it in the chain.** Every pressable
  surface here was written `.background(colour).clickable { }`, so the colour was painted outside the
  indication node and stayed exactly where it was while the label tilted inside it. On a 54 dp row
  that is invisible.
- Deliberately **not** covered: the library's own `KvadrantAppBarButton` — the decline ring, the back
  bar's ring — which does not tilt in kvadrant-ui either. That is the library's decision and changing
  it belongs there.

- AC: pressing a surface this product draws moves the surface, not only the words on it.
- AC: the control is still a control — role, enabled state and click action survive.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/Pressable.kt`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/components/TilePressTest.kt`

## What it turned out to be

**One order, six surfaces, and a number that tells them apart.** `Modifier.pressableSurface(colour)`
is `clickable(…).background(colour)` — the click first — and the five coloured surfaces that had it
backwards take it: the class tile, the offer's accept, the driver's advance bar and summary bar, the
tip chips. The two bordered ones (the shift's switch, the onboarding field) got the same swap by
hand, because a border is painted like a colour and stood just as still.

**Measured rather than judged.** `TilePressTest` composes a class tile, captures a resting frame,
holds a press with the clock stopped, captures again, and counts the tile's own **corner** pixels —
surface, never text:

| the click is | corner pixels that move | pixels that move overall |
|---|---|---|
| inside the colour (before) | **8 of 36** | 3 492 |
| outside it (after) | **35 of 36** | 5 856 |

The control was run both ways round rather than assumed, and the guard's threshold — half the corner
— is a line neither arrangement lands on by accident. Put the order back and it fails with *"only 8
of 36 corner pixels moved"*.

**The indication is deliberately not named at any call site.** `TiltIndication` is public and the
first attempt passed it explicitly; that would make each surface stop following the theme the day the
theme changed its mind. The helper takes the colour and leaves the indication to `LocalIndication`,
which is what "press feedback is the theme's" means.

**And `kvadrantTilt`, the library's own modifier, is not what a surface should wear.** It takes an
`onClick` of its own and carries no click semantics: a `performClick` on a node wearing it does
nothing — probed before the helper was written, not after. Wearing it would have cost every surface
its role, its keyboard and every UI test in this repository.

**No golden moved for this.** The indication is identity at rest, which is exactly why the guard had
to press one.
