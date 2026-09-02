---
id: B-26
title: "Rider and driver actually sign in, against a running shildik"
status: done
priority: P1
size: M
stage: stage-1-skeleton
---

# B-26 — Rider and driver actually sign in, against a running shildik

Split out of [B-09](B-09-browser-side-pkce.md) when the code half closed. That item built the PKCE
client and proved its algorithm against RFC 7636's own example; what it could not do is run a sign-in,
because two things it needs do not exist yet.

- **It needs an application to sign in from.** [B-01](B-01-decide-the-browser-route.md) settled the
  target on the day B-09 was built, so the rider and driver clients are still ahead. Until one of
  them exists there is no redirect to follow and no callback to receive. That is
  [B-28](B-28-the-client-application-shell.md) — which this item was recorded as blocked by B-09 on
  until B-09 closed and the real blocker had to be named.
- **It needs a shildik to sign in to.** There is no compose file in shildik's repository, so
  standing one up locally — provider, realm, a public client per role, redirect URIs — is part of
  this item rather than a precondition someone else meets.
- **It is what tests the browser's crypto.** B-09's suite runs on the JVM against the JDK provider;
  the `wasmJs` target compiles and nothing executes it. A sign-in that completes is what proves
  WebCrypto computes the same `S256` — and a sign-in that fails on the challenge is the only way
  that assumption can break, so it fails loudly rather than subtly.
- Deliberately **not** covered: Google as an identity provider end to end. That needs real
  credentials at a real Google project; the magic-link path exercises the same client code, since
  shildik's `authorize` makes the method its own page's business and the client names none.

- AC: a rider signs in with a magic link against a locally running shildik and the client holds a
  token the server accepts.
- AC: **met, by [B-34](B-34-a-browser-on-the-build-box.md) on 2026-09-02.** The same flow runs in
  the browser build, not only on the JVM — this is the criterion that covers WebCrypto. It was
  unmet when this item closed and moved to B-34 with the thing that blocked it; B-34 put a browser
  on the machine and the eight PKCE tests now run in Chrome as well as on the JDK.
- AC: a tampered `state` on the callback is refused, and the refusal is shown to have been reached
  rather than assumed.
- AC: how shildik is run locally is written down in the repository, so the next person does not
  rediscover the realm and client setup.
- Anchors: `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `shildik/server/src/commonMain/kotlin/ru/workinprogress/shildik/server/oidc/OidcRoutes.kt`

## What it turned out to be

**Three of the four criteria were met against a provider that was actually running; the fourth was
not met, and it is the one that needed a machine rather than code.**

The stand is `docker/compose.yaml` — shildik 0.2.0.8 and a Postgres, both bound to `127.0.0.1` on
18081 and 19001 rather than 8080 and 9000. That is not tidiness: the first attempt sat in `Created`
with an empty log because another project on the shared build box already held those ports, and a
container that never starts looks exactly like one that started and crashed. `docker/bootstrap-shildik.sh`
creates the realm, the **public** client `rider` with its redirect URI, and a user with a password —
that last through `PUT /admin/tenants/{realm}/users/{id}/password`, because creating a user does not
set one.

`docker/sign-in-flow.py` walks the whole dance on the standard library, and its fourth step is the
control: the same code with the wrong verifier must be refused. Without that step the first three
prove only that a code can be exchanged, not that the challenge was ever checked.

**The two halves are proved against each other, not each against a fixture.** `SignInAgainstShildikTest`
takes the URL, challenge and form this module builds and requires shildik to complete them.
`ProtectedRidesTest` then takes the token that sign-in produced and requires *this server* to accept
it — a token the rider client obtained, on a route the rider uses. Neither side proves this alone: a
client can be handed a token by a provider whose keys the server never fetches, and a server can
accept a token no client of ours can produce.

The server had **no authentication at all** before this item, which the criterion "a token the server
accepts" quietly assumed it did. `configureAuth` from shildik's own `oidc-auth-server` is installed
when — and only when — an `OidcConfig` is supplied, and `POST /api/rides` and the cancel route move
inside `authenticate(JWT_AUTH_OIDC)`. `/api/routes`, `/api/quotes` and the promo screen stay public:
a price and a road are facts about the city.

A switch that is off by default is a switch nobody notices is off, so both sides are tested. The
refusal runs unattended and points at an address nothing answers on — if the 401 needed the provider
to be reachable, it would be happening after a network call rather than before one. And the
acceptance carries its own negative control: the same request with one character of the signature
changed must be refused, because a validator that accepted anything would pass every other line.

What is **still** authenticated rather than authorised: `RideRequest` carries a `riderId` as a field,
so a token proves somebody signed in and not that the ride is theirs. That half is B-09's remainder
and is named where the routes are declared.

**AC2 was not met when this closed, and is met now.** The flow ran on the JVM against the JDK
provider; the browser build compiled and nothing executed it, because `wasmJsBrowserTest` was
disabled in three build scripts for want of a browser. That was the same wall
[B-10](B-10-crash-reports-from-the-browser.md) closed against, and after the third time it became a
piece of work rather than a footnote: [B-34](B-34-a-browser-on-the-build-box.md), which found that
the box could host a browser all along — a pinned Chrome for Testing is an unzip, and not one shared
library was missing. **The eight PKCE tests now run in Chrome**, so `S256` computed by WebCrypto is
the challenge shildik verifies rather than an assumption about it.
