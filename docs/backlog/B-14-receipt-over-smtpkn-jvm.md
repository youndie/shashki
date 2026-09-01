---
id: B-14
title: "The e-mail receipt runs on smtpkn's JVM target, gated by a test against Mailpit"
status: open
priority: P2
size: M
stage: stage-3-surface
---

# B-14 — The e-mail receipt runs on smtpkn's JVM target, gated by a test against Mailpit

Research §1.6d: smtpkn states that `linuxX64` is the only platform it is claimed to work on, and
that the JVM target compiles and its 175 tests run in CI but nothing has been released. TLS on the
JVM goes through `SSLEngine`, which is the one part *not* shared with the native path — so the
unclaimed part is the part that talks to a real server.

- **This is a feature of the project, not a defect.** A reference service is exactly what turns
  "compiles and runs in CI" into "claimed". What it must not be is an assumption.
- The rejected alternative is sending receipts from a JVM mail library. It would work, and it would
  remove the only thing this part of the demo is demonstrating.
- Anything that fails is reported upstream rather than routed around locally.

- AC: a receipt is sent through smtpkn's JVM target with TLS on and arrives in Mailpit, in the
  integration suite rather than by hand.
- AC: the smtpkn coordinate this adds is a release or a CI-numbered publish, not a `-SNAPSHOT`, and a clean checkout on an empty cache still builds. **Handed over from [B-13](B-13-pin-every-dependency.md)**, which closed Risk 3 for the graph as it stood and could not close it for a dependency nobody had added yet — smtpkn is one of the five the research recorded as pre-release.
- Anchors: `smtp-client/build.gradle.kts`, `smtp-tls-jvm`
