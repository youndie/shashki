---
id: screen-rider-promo
title: Promo
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/promo"
parent_feature: feature-server-driven-promo
calls_api:
  - endpoint-screens
source: rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/promo
---

# Screen: promo

## 0a. Code anchors

| What | File |
|---|---|
| View model | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/promo/ui/PromoViewModel.kt` |
| Screen / Content | `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/feature/promo/ui/PromoScreen.kt` |
| Renderers | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/` |

## 0. Entry point and visibility

- **Entry point:** `/promo`. Nothing in the product navigates to it yet — it is reached by address.
- **Shown when:** always.

## 1. Screen states

- **Loading**: nothing drawn.
- **Content**: whatever tree the server sent.
- **Degraded**: a component this build has no renderer for is drawn as a placeholder and the rest of
  the screen is unaffected — a golden exists of exactly that.
- **Error**: the server did not answer.

## 2. API integration

| Call | Contract | Endpoint document |
| :--- | :--- | :--- |
| `GET /api/screens/promo` | `KompotComponent` | [endpoint-screens](../api/endpoint-screens.md) |

## 3. Initialisation

**Input parameters:** none.

| Call | Condition | Result |
| :--- | :--- | :--- |
| `GET /api/screens/promo` | on open | the tree |

## 4. UI elements, top to bottom

**There are none to list, and that is what the screen is.** The elements are whatever the server sent;
what this side owns is the vocabulary — which component names have renderers, which tokens resolve —
and one action handler.

### 4.1. The action handler

- **On a `navigate`:** if the deeplink is `shashki://rides`, pop back to the class picker. Any other
  name is ignored.
- **Why ignored rather than followed:** a deeplink is a name, and a client that followed names it does
  not know would let a backend navigate somebody into a screen this build does not have.

## 5. Navigation (summary)

- `navigate shashki://rides` ──▶ [screen-rider-class-picker](screen-rider-class-picker.md)

## 6. Quirks

* **This screen is the whole of the server-driven surface, deliberately.** See
  [D11](../research/research-architecture.md): everything a rider can be charged for is native.
