---
id: B-32
title: "Which screens the server sends, and which the client draws"
status: question
priority: P1
size: M
stage: stage-3-surface
---

# B-32 — Which screens the server sends, and which the client draws

The brief's own summary of what shashki demonstrates includes "server-driven screens beside natively
drawn ones", and the kit's section 08 is titled "composition rules for the screens the server sends
as a tree". **Nowhere is it recorded which screens those are**, and the question surfaced when
somebody asked.

## What exists today

| | |
|---|---|
| Three components and their renderers, registered through kompot's KSP processor | `shared-ui/.../ui/kompot/` ([B-17](B-17-kompot-renderer-invariants.md)) |
| The kit's three renderer-side composition rules, each with a golden fed the payload that breaks it | same |
| A producer — anything on the server that builds a tree | **nothing** |
| A consumer — any screen in `:rider` that renders one | **nothing** |

So the mechanism is built and tested and joined to nothing at either end. `TripRow`,
`FareBreakdown` and `EarningsTile` were chosen because [B-17](B-17-kompot-renderer-invariants.md)
named them, and B-17 named them because the item was written that way — not because a decision put
them there.

## What the question actually is

Not "how much BDUI" but **what is the demo trying to show about it**. Three answers are coherent and
they are different products:

- **A panel the server owns.** One region of an otherwise native screen — the fare breakdown on the
  trip screen, say — arrives as a tree. It shows the seam at its narrowest and it is the cheapest to
  build; the three components already written fit it exactly.
- **A screen the server owns.** Something with no native version at all — a receipt, a history list,
  the driver's earnings — where the client contributes a renderer per component and nothing else.
  This is the case that shows a client surviving a component it does not know, which is the property
  kompot's degradation exists for and the only one that makes the three rules load-bearing.
- **A screen that changes without a release.** The same as above plus the demonstration: change the
  tree on the server, reload, watch it differ, with the old bundle still running. That is the whole
  argument for BDUI and it needs no more client code than the second — only a story to tell.

- **The kit constrains the answer and does not settle it.** Its rules are about accent surfaces,
  tile sizes and figures, which are shapes a *card* or a *grid* has. That points at panels and lists
  rather than at whole flows, and it is the strongest evidence in the artefacts.
- **Whatever is chosen, the server needs a producer**, and that is where the work is: kompot's
  server side, a route that answers a tree, and the same components declared once so the two sides
  cannot drift.
- Deliberately **not** covered here: live updates. `kompot-realtime` is a separate mechanism and
  §1.5a already decided against Redis; a tree that changes under the reader is a second demo.

- AC: research §2 names which screens or regions the server sends, with the reason.
- AC: whatever is named is built end to end — a route on the server, a tree on the wire, a screen in
  the bundle rendering it — because a mechanism joined to nothing at either end is what this item
  exists to end.
- AC: a client that meets a component it does not know draws the rest of the screen, and that is a
  golden rather than an assertion.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ServerDrivenComponents.kt`,
  `kompot/README.md`
