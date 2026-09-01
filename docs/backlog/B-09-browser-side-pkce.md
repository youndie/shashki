---
id: B-09
title: "Authorization code with PKCE from the browser is shashki's code"
status: open
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

- AC: rider and driver both sign in against a local shildik with magic link and with Google.
- AC: the verifier never leaves the client, and a test proves the challenge is `S256` and not `plain`.
- Anchors: `shildik/README.md`, `shildik/oidc-auth-client/build.gradle.kts`
