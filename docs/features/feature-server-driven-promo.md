---
id: feature-server-driven-promo
title: A screen the server owns
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries:
  - screen-rider-promo
api:
  - endpoint-screens
tags: [bdui]
---

# A screen the server owns

## 1. Overview

One screen in the rider application is not written in the client: the server sends a tree of
components and the client renders it. It is the promo screen, and it is deliberately the least
important screen in the product.

**The boundary is the point.** Everything a rider can be charged for is native; what a marketing
person might want to change on a Tuesday is server-driven. A product that put the class picker behind
a wire format would have made its most-looked-at screen the one nobody can test with a golden.

## 2. Business rules

* The server names only tokens the client can resolve. kompot's design tokens are open strings on
  purpose, so a token this server invents would silently fall back to a default and render in the
  wrong style.
* A component the client does not know renders as a placeholder and the screen keeps working.
* A `navigate` action carries a **name**, not a route. The client maps the names it knows and ignores
  the rest.
* A deeplink may not be an `http` URL — kompot refuses it, so a backend cannot walk somebody out of
  the application through an ordinary transition.

## 3. Flow

1. The client asks `GET /api/screens/promo`.
2. The server builds the tree in kompot's DSL and encodes it with a `Json` of its own — kompot needs
   `classDiscriminator = "type"` and its serializer modules, and the server's global content
   negotiation is configured for this product's DTOs.
3. The client decodes it, renders through the generated registry, and hands actions to a handler that
   knows one deeplink.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/PromoRouting.kt` |
| shashki-server | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/` — the renderers |
| shashki-server | `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/ScreenTokens.kt` |

## 5. Scenarios

### Scenario: every token the server names is one the client can resolve

* **Given:** the encoded tree
* **When:** every `style` and `color` in it is compared with `ShashkiTokens`
* **Then:** the sets are equal — checked on the **JSON**, because what a client can be wrong about is
  a name rather than a Kotlin type
* **Automated:** `shashki PromoTreeTest`

### Scenario: the action is registered too

* **Given:** the same document
* **When:** every `type` in it is collected
* **Then:** it is exactly `column`, `text`, `button`, `navigate` — actions and components share one
  discriminator, so a client that knew every component and not the action would render the button and
  do nothing when it was pressed
* **Automated:** `shashki PromoTreeTest`

### Scenario: a component this build does not know

* **Given:** a tree carrying a component the client has no renderer for
* **When:** it is rendered
* **Then:** the unknown component is drawn as a placeholder and the rest of the screen is unaffected
* **Automated:** `shashki AComponentThisBuildDoesNotKnow` — the fixture behind
  `bdui_a_component_this_build_does_not_know.png`

## 6. Out of scope

* Any screen a rider can be charged from. The boundary is stated in
  [D11](../research/research-architecture.md) and this feature is the whole of the server-driven side.
* Editing the tree without a deployment. There is no console and no storage; the tree is built in the
  handler.

## 7. Quirks

* **kompot's registry is generated into the common metadata, not per target.** Per target puts it
  where `commonMain` cannot see it, so a common composable cannot name its own renderers — see
  `shared-ui/build.gradle.kts`.
* **`@Serializable` without the serialization plugin compiles.** `:shared-ui` had no plugin and three
  tests failed at the first decode with "Serializer for class 'TripRow' is not found".
