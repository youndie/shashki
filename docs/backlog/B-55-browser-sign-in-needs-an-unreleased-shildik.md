---
id: B-55
title: "Browser sign-in cannot finish: the provider's CORS headers are unreleased"
status: open
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
