---
id: B-26
title: "Rider and driver actually sign in, against a running shildik"
status: open
priority: P1
size: M
stage: stage-1-skeleton
blocked_by: [B-28]
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
- AC: the same flow runs in the browser build, not only on the JVM — this is the criterion that
  covers WebCrypto.
- AC: a tampered `state` on the callback is refused, and the refusal is shown to have been reached
  rather than assumed.
- AC: how shildik is run locally is written down in the repository, so the next person does not
  rediscover the realm and client setup.
- Anchors: `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `shildik/server/src/commonMain/kotlin/ru/workinprogress/shildik/server/oidc/OidcRoutes.kt`
