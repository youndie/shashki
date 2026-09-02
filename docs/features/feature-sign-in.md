---
id: feature-sign-in
title: Sign in
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries: []
api:
  - endpoint-rides
tags: [auth]
---

# Sign in

## 1. Overview

The rider signs in against shildik with authorization code and PKCE, from the browser, and the token
that comes back is one this server accepts on the routes that change something.

**Neither half proves this on its own**, which is why the feature is one document rather than two: a
client can be handed a token by a provider whose keys the server never fetches, and a server can
accept a token no client of ours can produce.

## 2. Business rules

* The verifier never leaves the browser; what goes to the provider is its `S256` challenge.
* A callback whose `state` is not this attempt's is refused **before the code is spent** — in the
  client, where the exchange happens, rather than in whoever called it.
* Which sign-in method a person uses — a password, a magic link, Google — is shildik's own page and
  not this product's business. The client names no method.
* Verification is installed only when a provider is configured. A demo pointed at nothing runs with
  the rider's routes open, and the log says so.
* The server verifies the signature and the expiry and requires the token's `azp` to be this client.
  It deliberately does **not** check `iss`: during a provider migration a service has to accept tokens
  from both.

## 3. Flow

1. The client builds the authorize URL from shildik's own `@Resource` classes — no path assembled from
   strings.
2. shildik serves its sign-in page; the person signs in there; the browser comes back to the redirect
   URI with a code and the state.
3. The client checks the state, then exchanges the code with the verifier that never left the process.
4. The token goes on `POST /api/rides` and the cancel route; the server verifies it with shildik's own
   validator, which fetches JWKS itself and caches it for a day.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `server/src/main/kotlin/io/github/youndie/shashki/server/feature/auth/AuthConfig.kt` |
| shashki-server | `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt` — the browser half |
| shashki-server | `docker/bootstrap-shildik.sh` — the realm, the public client, a user |

## 5. Scenarios

### Scenario: an attempt this module built is one shildik completes

* **Given:** a running shildik with a realm and a public client
* **When:** the client's authorize URL is followed, the person signs in and the code is exchanged
* **Then:** a token comes back
* **Automated:** `shashki SignInAgainstShildikTest`

### Scenario: WebCrypto computes the same challenge as the JDK

* **Given:** the same `commonTest` suite
* **When:** it runs in a real browser as well as on the JVM
* **Then:** both pass — which is what says the browser's `S256` is the one shildik verifies
* **Automated:** `shashki PkceTest` (`:auth-client:wasmJsBrowserTest`)

### Scenario: a tampered state

* **Given:** a callback carrying somebody else's `state`
* **When:** the client is asked to build the token request
* **Then:** it refuses with "different sign-in attempt" and the code is never sent
* **Automated:** `shashki SignInAgainstShildikTest`

### Scenario: the server accepts what the client obtained

* **Given:** a token from the full PKCE dance
* **When:** it is used on `POST /api/rides`
* **Then:** `201` — and the same request with one character of the signature changed is `401`
* **Automated:** `shashki ProtectedRidesTest`

### Scenario: no token at all

* **Given:** a server with a provider configured
* **When:** a ride is requested with no `Authorization` header
* **Then:** `401`, before anything is verified — the test points the validator at an address nothing
  answers on, so a refusal that needed the network would fail
* **Automated:** `shashki ProtectedRidesTest`

## 6. Out of scope

* **Google as a provider end to end.** That needs real credentials at a real Google project; the
  magic-link path exercises the same client code, because shildik's `authorize` makes the method its
  own page's business.
* Refresh tokens, sign-out, and any session on this server. There is none: the token is the session.
* The driver's sign-in. The driver's routes carry their tier as a named hole until B-09.

## 7. Quirks

* **There is no sign-in screen.** `RiderRoute.SignIn` exists and renders the class picker: the flow is
  a redirect out of the application and back, so what would be on that screen is a button nobody has
  needed yet.
* **The rider's email is on the order saga's payload**, taken from the token when there is one. It is
  what the receipt is sent to, and `null` is why a stand with no provider sends none.
* **The rider application never sends the token, and this document found that out.** `SignInAttempt`
  is built, tested against a live provider and proven in a browser; nothing in `:rider` calls it, and
  the application's HTTP client attaches no `Authorization` header. So the two halves are each proven
  against the other and **the product joins them nowhere** — with a provider configured, the rider
  bundle gets 401 on every ride route. That is [B-41](../backlog/B-41-the-rider-actually-signs-in.md),
  and it is why `docker/compose.yaml` has `SHASHKI_OIDC_ISSUER` commented out.
