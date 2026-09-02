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

Public: it is marketing, and it names nobody.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `GET /api/screens/promo` | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/PromoRouting.kt` |

## The body is a component tree, not a DTO

The response is a kompot document: `column`, `text`, `button`, and a `navigate` action. What makes it
safe to send is that the vocabulary is closed at both ends — the tokens are `ShashkiTokens` in
`:protocol`, and `PromoTreeTest` walks the **encoded JSON** rather than the object graph, because what
a client can be wrong about is a name.

**Encoded with its own `Json`, not through `ContentNegotiation`.** The tree needs kompot's
`classDiscriminator = "type"` and its serializer modules; the server's global negotiation is
configured for this product's own DTOs, and one of the two would have had to lose.

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
