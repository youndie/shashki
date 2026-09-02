---
id: B-17
title: "The kit's composition rules live in the kompot renderer, not in the protocol"
status: done
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

- ~~AC: a fixture per rule, each fed a payload that breaks it, each rendering the degraded form
  rather than throwing.~~ **Done, 2026-09-02.** `kompot_two_accent_surfaces` (both rows ask, the
  second is chrome), `kompot_a_tile_size_the_grid_has_no_shape_for` (four tiles sent, three drawn),
  `kompot_a_second_figure_in_a_card` (the fare at 54, the line that asked for 32 capped at 19).
- ~~AC: the server-driven subset — `TripRow`, `FareBreakdown`, `EarningsTile` — registers through
  `@KompotComponentMarker` and appears in the generated registry.~~ **Done, and asserted rather than
  looked at.** `GeneratedRegistryTest` requires the renderer map to be exactly those three, decodes a
  server payload through `generatedShashkiUiSerializersModule` into `EarningsTile`, and controls it
  by decoding the same payload *without* the module and getting kompot's `UnknownComponent`.
- Anchors: `kompot/kompot-registry-processor/src/main/kotlin/io/github/youndie/kompot/registry/processor/KompotRegistrySymbolProcessor.kt`

## What it turned out to be

**The rules were the easy part. Making a fixture able to break them was the work.**

Two of the three violations were expressible from the start — two rows can both ask for the accent, a
tile can carry `size = 3`. The third was not: `FareBreakdown` had no way for the server to ask for a
second figure, so the cap had nothing to cap and the fixture would have photographed a rule that
could not be broken. `FareLine` grew an `emphasis` for that reason, and the component's own KDoc now
says why the shape is permissive: a protocol that *could not* express the violation would enforce the
rule by construction and would also be one nobody could evolve, because the server that sends it is a
different deployment from the client that draws it.

**A build-configuration mistake with no compile-time symptom.** `:shared-ui` had no
`kotlinSerialization` plugin. `@Serializable` resolved anyway — the annotation arrives transitively —
so the components compiled, the generated registry compiled, and the first decode threw
`Serializer for class 'TripRow' is not found`. Nothing in this repository had decoded anything before,
which is why a module with six plugins looked complete. Research §1.7c has it.

**A gap in kompot, named rather than worked around.** Its `KompotDegradationSink` exists because "a
hole is reported by nobody" — and its three kinds are all about a *type* or an *action* being
unknown. There is none for a property outside its allowed set, so a dropped tile is invisible.
Reporting it as `UNRENDERABLE_COMPONENT` would be a lie: the component is perfectly renderable, its
size is not. The drop stays silent and `EarningsTileRenderer` says so at the point it happens.

**The registry test earns its keep through its control.** Asserting that three classes are in a
generated map is nearly tautological; the control decodes the same server payload *without* this
module's registrations and gets `UnknownComponent`. That is what makes the first assertion mean
"the wire works" rather than "KSP ran".
