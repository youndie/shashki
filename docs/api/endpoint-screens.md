---
id: endpoint-screens
title: Screens the server owns
type: api_endpoints
status: active
services:
  - shashki-server
contract_source:
  - shashki:protocol ShashkiTokens
  - kompot:kompot-core KompotComponent
parent_feature: feature-server-driven-promo
---

# API: server-driven screens

## Routes — all of them, no exceptions

| Method and path | Auth tier | Purpose |
|---|---|---|
| `GET /api/screens/promo` | public | one screen as a tree of components, rendered by the client |
| `POST /api/screens/degradations` | public | what a client could not draw, and what it drew instead |

Public: the first is marketing and names nobody; the second names a component and a screen and
nobody, and behind a token it would stop reporting exactly when a build is broken enough that signing
in does not work.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET /api/screens/promo` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/PromoRouting.kt` |
| `POST /api/screens/degradations` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/Degradations.kt` |

## The body is a component tree, not a DTO

The response is a kompot document: `column`, `text`, `button`, and a `navigate` action. What makes it
safe to send is that the vocabulary is closed at both ends — the tokens are `ShashkiTokens` in
`:protocol`, and `PromoTreeTest` walks the **encoded JSON** rather than the object graph, because what
a client can be wrong about is a name.

**Encoded with its own `Json`, not through `ContentNegotiation`.** The tree needs kompot's
`classDiscriminator = "type"` and its serializer modules; the server's global negotiation is
configured for this product's own DTOs, and one of the two would have had to lose.

The tree uses the 0.38 vocabulary where it has a use: the headline is `heading: true`, the one place
a screen reader can move to (SPEC.md §4.11). It is built through `TextComponent` rather than the DSL's
`text(…)`, which cannot set the field yet — youndie/kompot#205.

## What a client could not draw

`POST /api/screens/degradations` takes `DegradationReport` from `:protocol` and answers `202`.

| Field | What it is |
|---|---|
| `kind` | kompot's `KompotDegradationKind` by name — `UNKNOWN_COMPONENT`, `UNRENDERABLE_COMPONENT`, `UNKNOWN_ACTION` |
| `componentType` | the wire name the client met |
| `screen` | which screen, so a count reads as "this screen is broken for somebody" |
| `outcome` | kompot's `KompotDegradationOutcome` by name — `NOTHING`, `PLACEHOLDER`, `SERVER_FALLBACK`; `UNREPORTED` when absent |
| `drawnAsFallback` | read, never written: what a bundle built before #43 sends instead of `outcome` |

**The outcome is the field the count is for.** A hole, the toolkit's placeholder and the server's own
equivalent are different events, and only the last is a choice somebody made; the boolean this
replaced was `true` for the second and third alike. `DegradationCounter` counts by kind and type, and
by outcome within them. **A report from an older bundle is accepted** and counted as `UNREPORTED`:
the server decodes strictly, and a report must never be the thing that fails.

## What the client does with a `navigate`

The deeplink is a **name**, not a route: the client maps the ones it knows and ignores the rest, which
is what keeps a backend from navigating somebody into a screen this build does not have. kompot forbids
`http` in a deeplink for the same reason, and `PromoTreeTest` checks that assumption where it is made
rather than trusting it.

## Errors

| Condition | Status | Body |
|---|---|---|
| — | — | the tree is built in the handler and cannot fail on data it does not read |

**A component the client does not know is not an error either.** It renders as `UnknownComponent` and
the screen keeps working, which is kompot's whole degradation story — and is why
`bdui_a_component_this_build_does_not_know.png` is a golden.
