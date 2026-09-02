---
id: B-14
title: "The e-mail receipt runs on smtpkn's JVM target, gated by a test against Mailpit"
status: done
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

- ~~AC: a receipt is sent through smtpkn's JVM target with TLS on and arrives in Mailpit, in the
  integration suite rather than by hand.~~ **Done, 2026-09-02.** `ReceiptOverSmtpTest` sends through
  `SslEngineTlsProvider` over `STARTTLS` into Mailpit and finds the message by ride id in Mailpit's
  own API. **With a control**, because "a mail arrived" does not prove a certificate was checked:
  the same code pointed at a CA that signed nothing must fail, and does.
- ~~AC: the smtpkn coordinate this adds is a release or a CI-numbered publish, not a `-SNAPSHOT`~~
  **— pinned by build metadata, which is B-13's own documented fallback, because smtpkn has released
  nothing.** Only `0.1.0-SNAPSHOT` exists, so the catalog names the resolved build:
  `0.1.0-20260809.051147-2`. **And it is two numbers, not one**: the same publish left
  `smtp-transport-ktor` at build **1** while the other five modules are at build **2**, on the same
  timestamp, so a single version reference across the set does not resolve. It fails loudly with
  "could not find", which is the good case — the fallback quietly assumes one build number per
  publish and this publish has two.
- Anchors: `smtp-client/build.gradle.kts`, `smtp-tls-jvm`

## What it turned out to be

**The JVM target works, and using it for real found a defect that is not in the JVM target.**

The receipt goes out through `SSLEngine`, over a `STARTTLS` handshake against a certificate that is
actually verified, and lands in Mailpit. Risk 4 asked whether the unclaimed half of smtpkn talks to a
real server; it does. The control is what makes that worth saying — a send that succeeded would prove
a message arrived and nothing about the certificate, so the same code is pointed at an unrelated CA
and required to fail.

**What broke is `isEncrypted`.** `SmtpSession.encrypted` is declared `private var encrypted = false`
and assigned nowhere in the module, so the flag is permanently false — on every platform, `linuxX64`
included. `authenticate()` refuses to run when it is false, so **`AUTH` after a successful
`STARTTLS` always throws**, unless the caller passes `allowOverPlaintext = true`: a flag whose name
asserts the opposite of what is true. The library's own README shows exactly that sequence as its
usage example.

**Why its own suite does not catch it** is the part worth keeping. Those tests pass
`allowOverPlaintext = true` throughout, with a comment saying that beats "pretending the scripted
transport" is encrypted — an honest decision, and the reason nothing ever runs a real provider over a
real connection and then asks the flag. Being truthful about the scripted case is what hid the
defect in the unscripted one.

**Nothing is routed around here**, which the item asked for by name. Mailpit needs no credentials, so
the receipt path does not call `authenticate` and is not blocked; a real relay would be, immediately.
The check `SmtpReceiptSender` would naturally make — "am I encrypted?" — cannot be made, so the
comment there says why and points at the control that demonstrates it instead. Research §1.6d1 has
the four addresses. **Not filed upstream**: that is asked about first.

**One more thing about pinning.** B-13's fallback for an unreleased library is "pin the snapshot by
build metadata". That works, and it needs *per-module* numbers rather than one: this publish is build
2 for five modules and build 1 for a sixth. A single reference fails to resolve, which is the good
outcome; the assumption behind the fallback is still worth writing down, because the next library
might differ in a way that resolves to something.
