---
id: B-55
title: "Browser sign-in cannot finish: the provider's CORS headers are unreleased"
status: done
priority: P0
size: XS
stage: stage-6-what-running-it-said
---

# B-55 — Browser sign-in cannot finish: the provider's CORS headers are unreleased

Both bundles run the code exchange in the page, so the browser `fetch`es the provider's token
endpoint from a different origin — a different port is a different origin, in every configuration
this stand supports. `ghcr.io/youndie/shildik:0.2.0.8` answers that request without
`Access-Control-Allow-Origin`, so Chrome refuses to hand the response to the page. Measured: the
redirect works, the login form works, the code comes back on `/driver/callback`, and then

```
Access to fetch at '.../realms/shashki/oauth2/token' from origin 'http://127.0.0.1:18080'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present
```

The failure is uncaught and the page goes blank (see [B-56](B-56-an-uncaught-failure-is-a-blank-page.md)).
shildik already has the fix — `PageReadable.kt`, written for exactly this case — and it is in no
published image: `latest`, `0.2.0.8` and `sha-ed689c5` are all one digest, built four days before the
commit. Filed as [youndie/shildik#22](https://github.com/youndie/shildik/issues/22).

- **This item is a pin, not a workaround.** When shildik publishes, `docker/compose.yaml` moves one
  version string and the whole browser half of sign-in works. Anything shashki does in the meantime —
  proxying the provider through its own origin, moving the exchange to the server — is a second
  design for a problem that is already solved upstream.
- **What it changes today is the documentation, which currently overstates.** `feature-sign-in` and
  the research say both bundles sign in; against every published provider image they cannot. That
  sentence is the part worth fixing now, with the reason and the issue beside it.
- Deliberately **not** covered: a non-browser client is unaffected — the same four requests from a
  plain HTTP client succeed, PKCE control included, which is why no test caught this.

- AC: with a shildik carrying the CORS commit, a person opens the driver bundle, signs in, and
  reaches the documents screen without touching `sessionStorage`.
- AC: until then, `feature-sign-in` and research §5 say what does not work and name the issue.
- Anchors: `docker/compose.yaml`, `docs/features/feature-sign-in.md`,
  `docs/research/research-architecture.md`

## What it turned out to be

**Released the same evening, and the pin was the whole fix.** shildik published `0.2.0.13` from
`a62db6d` in answer to [issue #22](https://github.com/youndie/shildik/issues/22); `docker/compose.yaml`
moved one version string. The token endpoint now answers a cross-origin `POST` with
`Access-Control-Allow-Origin: *`, `Allow-Headers: Content-Type` and `Allow-Methods: GET, POST,
OPTIONS`, and deliberately without `Allow-Credentials` — checked against the running stand with
`curl` before a browser was pointed at it.

**Then the flow was walked end to end in a real browser, which is what this item existed for.**
Pressing *go online* with nobody signed in redirects to the provider; the password form posts;
`/driver/callback` comes back with the code; the exchange succeeds; and the shift screen returns
carrying `rider@example.com` — the subject, drawn by [B-53](B-53-the-driver-bundle-cannot-go-online.md)'s
identity. Before the bump the same walk ended on a blank page, which is
[B-56](B-56-an-uncaught-failure-is-a-blank-page.md)'s subject and still open.

**What this says about the stack is worth keeping.** The fix was in shildik's `main` for four days,
written for exactly this consumer, and no published image carried it — `latest`, `0.2.0.8` and
`sha-ed689c5` were one digest. A pinned dependency does not mean a *released* one, and "the fix is
merged" is not a sentence about anything a deployment can pull. The stack's own answer was a manual
image workflow that nobody had run; shildik is filing the release trigger separately.
