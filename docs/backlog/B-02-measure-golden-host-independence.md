---
id: B-02
title: "Measure whether shashki's goldens are host-independent"
status: open
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

- AC: `DriverOffer` — 54 sp figures, tabular numerals, a ruble sign — recorded on macOS and on
  Linux, and the two PNGs diffed, with the percentage recorded.
- AC: the result is written into research §3 Risk 2 as a fact, and `verifyOnCheck` is switched on or
  the mac-only gate is stated in CI.
- Anchors: `kvadrant-ui/CLAUDE.md`,
  `kvadrant-ui/kvadrant-core/src/desktopTest/kotlin/io/github/youndie/kvadrant/type/PortableTypography.kt`
