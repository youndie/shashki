---
id: B-17
title: "The kit's composition rules live in the kompot renderer, not in the protocol"
status: open
priority: P2
size: M
stage: stage-3-surface
blocked_by: [B-03]
---

# B-17 — The kit's composition rules live in the kompot renderer, not in the protocol

Research §1.7 read the kit's section 08 at source. Three of its six rules are statements about what
the client does when the server sends something the rule forbids: a second accent surface degrades to
chrome, an unknown tile size is dropped rather than guessed, and a figure the server marks primary
goes to 54 with nothing else in the card above 19.

- **Put them in the renderer.** A protocol can describe the allowed shape; only the renderer can
  decide what happens to the disallowed one. kompot's own posture is degrade-rather-than-crash, so
  this is the toolkit's grain and not a fight with it.
- The rejected alternative is server-side validation. It would work today and put the guarantee on
  the wrong side of the wire: a second implementation of the server drops it silently, and the client
  that trusted it renders two accent surfaces with no error anywhere.
- The grid comes from `ShashkiMetrics`, so this item inherits whatever
  [B-15](B-15-answer-the-kits-open-questions.md) answers about 4/3 and takes no decision of its own.

- AC: a fixture per rule, each fed a payload that breaks it, each rendering the degraded form rather
  than throwing.
- AC: the server-driven subset — `TripRow`, `FareBreakdown`, `EarningsTile` — registers through
  `@KompotComponentMarker` and appears in the generated registry.
- Anchors: `kompot/kompot-registry-processor/src/main/kotlin/io/github/youndie/kompot/registry/processor/KompotRegistrySymbolProcessor.kt`
