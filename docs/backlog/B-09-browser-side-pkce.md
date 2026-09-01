---
id: B-09
title: "Authorization code with PKCE from the browser is shashki's code"
status: done
priority: P1
size: S
stage: stage-1-skeleton
---

# B-09 — Authorization code with PKCE from the browser is shashki's code

The brief books sign-in as "shildik modules". Research §1.6c found that `oidc-auth-client` publishes
jvm and linuxX64 only — there is no browser variant of it anywhere in shildik, whose targets are jvm,
linuxX64, linuxArm64 and macosArm64.

- **The browser half is small and it is ours.** A redirect, a verifier, an `S256` challenge and a
  token exchange. The challenge needs SHA-256, which in a browser is WebCrypto and therefore
  asynchronous — that is the one part that shapes the code rather than filling it in.
- The rejected alternative is adding a browser target to shildik. That changes a library to save a
  handful of calls in one consumer, and shildik's server side is what shashki is demonstrating.
- The server keeps verifying tokens through `oidc-auth-server`, unchanged.

- ~~AC: rider and driver both sign in against a local shildik with magic link and with Google.~~
  **Split out as [B-26](B-26-sign-in-end-to-end.md), 2026-09-02.** There is no rider or driver
  application to sign in *from* yet — [B-01](B-01-decide-the-browser-route.md) settled the target
  the same day and the apps are later work — and no shildik instance to sign in *to*. Leaving the
  criterion here would have made this item permanently almost-done; the code it was gating is
  finished and tested, and the end-to-end run is its own item with its own prerequisites.
- ~~AC: the verifier never leaves the client, and a test proves the challenge is `S256` and not
  `plain`.~~ **Done, 2026-09-02.** The whole authorize URL is searched for the verifier rather than
  the parameter list being inspected, and `plain` is not rejected at run time — the module cannot
  express it. The challenge is checked against RFC 7636 Appendix B's own worked example, so the
  expectation comes from the specification and not from running the code.
- Anchors: `shildik/README.md`, `shildik/oidc-auth-client/build.gradle.kts`

## What it turned out to be

**Smaller than the item, for three reasons it took reading shildik to find.**

*There is no Google branch.* shildik's `authorize` serves shildik's own sign-in page, and the choice
between a magic link and Google is made there, returning through `callback/{method}`. The client
never names a method, so "sign in with Google" and "sign in with a magic link" are the same redirect
on this side. The item's first acceptance criterion read as if the client had two flows; it has one.

*There is no hand-written WebCrypto.* shildik verifies PKCE with `dev.whyoleg.cryptography`, and
that library publishes `wasm-js` variants of core, random and the WebCrypto provider. So the browser
gets SHA-256 and a cryptographic random from the same primitive the provider checks against, and the
asynchrony the item flagged as "the one part that shapes the code" stays exactly where it should —
in the signature of `Pkce.challenge`, which is `suspend` for everyone because it cannot be anything
else for the browser.

*There is nothing to reuse and nothing duplicated.* shildik has `Pkce.matches` and no generator,
because generating is the client's job. The two halves now share a primitive rather than agreeing by
coincidence.

**What is bigger than the item said.** shildik is not missing a browser *variant* of one library; it
is missing a browser **target** on modules whose dependencies already publish one. `shared-oidc`
holds the `@Resource` types for `authorize`, `token`, `jwks` and `callback` and depends on nothing
but `ktor-resources` and `kotlinx-serialization-json` — both multiplatform including `wasmJs`. Its
absence is why `SignInAttempt.authorizeUrl` builds a path out of string pieces, which is the one
place in this repository where an endpoint exists as a string; everywhere else `@Resource` makes
that a compile error. Adding `wasmJs` to `shared-oidc` would delete that. **Not filed upstream** —
that is asked about first — but recorded in research §1.6c1 so it is proposed rather than forgotten.

**What the tests do not cover, said plainly.** Eight tests run, all on the JVM, where
`CryptographyProvider.Default` is the JDK one. The `wasmJs` target compiles — `check` depends on it —
but nothing executes the WebCrypto path, because the build box has no browser and this module has no
`wasmJs` test source set. So "the challenge is `S256` and not `plain`" is proven for the algorithm
and for the JVM provider; that WebCrypto computes the same SHA-256 is the assumption B-26 tests by
signing in.
