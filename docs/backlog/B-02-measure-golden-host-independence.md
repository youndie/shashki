---
id: B-02
title: "Measure whether shashki's goldens are host-independent"
status: done
priority: P0
size: S
stage: stage-0-unknowns
---

# B-02 — Measure whether shashki's goldens are host-independent

The handoff carries kvadrant's rule that goldens are recorded on macOS. Research §1.2a found that
the rule protects kvadrant's *calibration* suite — which fits a variable font's weight by counting
ink — and not a consumer that only photographs screens. viddik's own position is that goldens are
portable given a bundled font and pinned hinting. Which of the two applies to shashki is a
measurement nobody has taken.

- **It decides where the acceptance gate lives.** Builds in this stack run on the Linux box; a gate
  that only runs on one laptop is a gate that runs rarely, and the answer changes how CI is written.
- The rejected alternative is inheriting the rule. Inherited constraints are hypotheses about
  somebody else's code.
- Not covered: making them portable if they are not. That is a separate item written against
  whatever the diff shows.

## What it turned out to be

**Portable.** `skeleton_themes` recorded on macOS verifies unchanged on Ubuntu under WSL2, with the
ramp pinned through `ViddikPlatformTextStyle`. So the constraint was kvadrant's own and not an
inherited property of this stack, exactly as research §1.2a suspected — and suspecting is not the
same as knowing, which is why this item existed.

- ~~AC: one text-heavy fixture recorded on both hosts and diffed, with the percentage recorded.~~
  Done. The fixture is `skeleton_themes` rather than `DriverOffer`, because B-04 has not built
  `DriverOffer` yet and the property being measured — does a bundled font rasterise identically
  under a pinned hinting setting — does not depend on which glyphs are on the screen.
- ~~AC: the result written into research §3 Risk 2, and `verifyOnCheck` switched on or the mac-only
  gate stated in CI.~~ Done: Risk 2 is closed with the numbers, and `verifyOnCheck = true` puts the
  goldens inside `./gradlew check` on every host.

**The failure is the part worth keeping.** A passing comparison proves nothing on its own; this one
was made to fail first. One extra character in a label moves **627 of 329 160 pixels — 0.19 %
against a 0.05 % tolerance** — and the check goes red; remove the character and it goes green. Both
under `--rerun-tasks`, so neither answer came from the build cache.

**Two controls before it were vacuous and both looked convincing.** The first took `$?` after a
pipe and read the exit status of `tail`. The second edited the fixture on the remote machine, where
the one-way replica reverted the edit before Kotlin compiled — the task ran, passed, and tested the
original file. The sync had to be paused before the control was a control. Anything measured on that
replica has to be produced and read inside one invocation, or not believed.

- Anchors: `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/SkeletonFixtures.kt`,
  `shared-ui/src/desktopTest/kotlin/io/github/youndie/shashki/ui/PortableTypography.kt`,
  `shared-ui/build.gradle.kts`
