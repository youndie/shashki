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

> **Amended 2026-09-02: this does not work in a browser today, and the reason is not in this
> repository.** Both bundles run the code exchange in the page, so the token request is cross-origin
> — a different port is a different origin, in every configuration this stand supports — and
> `ghcr.io/youndie/shildik:0.2.0.8` answers it without `Access-Control-Allow-Origin`, so the browser
> refuses to hand the response to the page. shildik has the fix on `main` and has not published it
> ([youndie/shildik#22](https://github.com/youndie/shildik/issues/22)); the pin is
> [B-55](../backlog/B-55-browser-sign-in-needs-an-unreleased-shildik.md). Everything below is true
> of a non-browser client and of the bundles once that image ships — the redirect, the parked
> attempt, the code on the callback and the verifier are all exercised and correct.

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

### Scenario: the application signs in and orders with what it got

* **Given:** a running shildik and a running server that requires a token
* **When:** the application's own `Session` runs the flow and orders a ride
* **Then:** `201` — and the same request without the token is `401`
* **Automated:** `shashki SignInJoinsUpTest`

### Scenario: the verifier never goes out through the address bar

* **Given:** an attempt about to redirect
* **When:** the URL it hands the browser is examined
* **Then:** it carries the challenge and the state, and not the verifier
* **Automated:** `shashki SessionTest`

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
* **The provider must have one address, and a container network makes that awkward.** The validator
  reads `jwks_uri` out of the discovery document, which carries the *issuer's* own address — so a
  server given an internal name is then sent to an address only a browser can reach, and refuses
  every token with a 401 that looks exactly like a bad token. The stand uses the machine's own
  address for both; `SHASHKI_HOST` is why.
* **There is still no sign-in on open, and that is the tier rather than an omission.** Prices are
  public, so the class picker loads anonymously and the redirect happens on the first protected
  call.
