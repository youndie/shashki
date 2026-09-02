---
id: B-33
title: "The three upstream fixes landed; take them and delete what they replace"
status: open
priority: P1
size: S
stage: stage-3-surface
---

# B-33 — The three upstream fixes landed; take them and delete what they replace

Three measurements in this repository ended the same way: a workaround, and an issue explaining what
the real fix was. All three were made upstream and published, so the workarounds are now debt rather
than necessity — and debt with an expiry date already written into its own comment.

| Issue | What shashki did instead | What it can do now |
|---|---|---|
| [youndie/shildik#20](https://github.com/youndie/shildik/issues/20) — `shared-oidc` had no browser target | `SignInAttempt.authorizeUrl` assembles a path out of string pieces, plus a hand-written percent-encoder | `shared-oidc-wasm-js` exists at 0.2.0.13; the `@Resource` classes build the URL |
| [youndie/katcher#32](https://github.com/youndie/katcher/issues/32) — `shared` had no browser target | `CrashReport` is a copy of `CreateReportParams` | `shared-wasm-js` at 0.6.40; use the original |
| [youndie/smtpkn#4](https://github.com/youndie/smtpkn/issues/4) — `isEncrypted` was never assigned | `SmtpReceiptSender` cannot ask whether it is encrypted and says so in a comment | build `0.1.0-20260902.062954-3` sets it (`SmtpSession.kt:372`); the check the sender wanted becomes possible |

- **This is deletion, mostly.** Two copies of somebody else's wire contract and one encoder go away;
  what replaces them is a dependency and an import. The third is a `check` that could not be written.
- **The KDocs that explain the workarounds go with them.** Each says "this is a copy and that is a
  defect, not a design" and points at the issue; leaving those beside code that no longer copies
  anything would be worse than never having written them.
- **Research §1.6b1, §1.6c1 and §1.6d1 are amended, not deleted.** They record measurements that were
  true and the rule is that the wrong idea stays with its correction beside it — these are the cases
  the rule exists for, because "why is there a copy of this type" is a question somebody will ask
  again.
- The rejected alternative is leaving it. A copy that works today is exactly the thing that goes
  stale in silence, and the argument for filing the issues was that the copy is a future bug.

- AC: `:auth-client` builds its authorize URL from `shared-oidc`'s `@Resource` classes, and the
  hand-written percent-encoder is gone. The test that searches the whole URL for the verifier still
  passes, which is what says the change was safe.
- AC: `:crash-client` sends katcher's own `CreateReportParams`, and `CrashReport` is deleted.
- AC: `SmtpReceiptSender` checks `session.isEncrypted` after `startTls`, and the run against Mailpit
  passes with it — which is the assertion B-14 wanted and could not make.
- AC: every coordinate is a release or a build pinned by metadata, and a clean checkout on an empty
  cache still builds.
- Anchors: `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `crash-client/src/commonMain/kotlin/io/github/youndie/shashki/crash/CrashReport.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/data/SmtpReceiptSender.kt`
