---
id: B-32
title: "Which screens the server sends, and which the client draws"
status: done
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

- ~~AC: research §2 names which screens or regions the server sends, with the reason.~~ **Done,
  2026-09-02: [D11](../research/research-architecture.md#d11-the-server-owns-one-screen-and-it-is-the-promo).**
  One screen, no native version, nothing in the ride flow.
- ~~AC: whatever is named is built end to end — a route on the server, a tree on the wire, a screen
  in the bundle rendering it.~~ **Done.** `GET /api/screens/promo` builds the tree in kompot's own
  DSL out of kompot's own components; `ServerScreen` in `:shared-ui` renders it through three
  registries; `/promo` is a route in the rider with a handler that maps the one deeplink it knows and
  ignores the rest.
- ~~AC: a client that meets a component it does not know draws the rest of the screen, and that is a
  golden rather than an assertion.~~ **Done:** `bdui_a_tree_the_server_sent` and
  `bdui_a_component_this_build_does_not_know` are the same tree with one node replaced by a type this
  build has never seen. The second draws everything around the hole.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ServerDrivenComponents.kt`,
  `kompot/README.md`

## What it turned out to be

**The mechanism was built and joined to nothing at either end, and nobody had noticed.**

B-17 wrote three components, three renderers, the KSP registry and three goldens of the composition
rules, and every one of them was correct. `grep` for kompot in `server/src` and `rider/src` returned
nothing: no producer, no consumer. `TripRow`, `FareBreakdown` and `EarningsTile` were the
"server-driven subset" because the item said so, and the item said so because I wrote it that way
while planning the backlog — a formulation that became a fact by being in a file.

**The decision is in D11 and the reasoning is worth repeating here**: a panel inside a native screen
never reaches the degradation, because there is always something beside it that renders. A screen
with no native version has nowhere to fall back to, so either the client keeps drawing around a
component it does not know or the screen is blank — and now that is two goldens rather than a claim.

**The interesting boundary is not how much, it is which direction.** The server names roles —
`page_title`, `accent` — and never values. There is no colour on the wire, no size, no font. So a
backend cannot paint an unreadable screen, because it has no way to say what a colour is; and a tree
from a server that has never heard of this kit renders in this kit. That property is kompot's, and
`ShashkiDesignSystem` is the whole of shashki's side of it.

**Three things the toolkit made me do differently than I would have.**

- `decodeKompotComponent` rather than a reified `decodeFromString`. kompot-core ships both helpers
  and its own source explains why: the polymorphic bases are plain interfaces, so the reified call
  resolves by reflection on the JVM and throws on Wasm. It would have passed every test here and
  failed in the browser.
- The registry had to be generated into the **common metadata**, not per target. Per target puts it
  in `desktopMain` and `wasmJsMain`, where `commonMain` cannot see it — so a common composable cannot
  name its own renderers. That also made viddik's per-target processor read a directory this one
  writes, which Gradle rightly refuses until the dependency is declared.
- The `type` discriminator covers **actions as well as components**. A client that knew every
  component and not the action would render the button and do nothing when it was pressed; the test
  pins both together for that reason.

**What is deliberately still missing.** No degradation sink is bound, so the hole is invisible to the
deployment that made it — the other half of the gap §1.7c records. And nothing demonstrates the
actual argument for BDUI yet: changing the tree on the server and watching an unchanged bundle show
something different. That needs no more client code, only a story, and it is not this item.
