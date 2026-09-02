---
id: B-33
title: "The three upstream fixes landed; take them and delete what they replace"
status: done
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

- ~~AC: `:auth-client` builds its authorize URL from `shared-oidc`'s `@Resource` classes, and the
  hand-written percent-encoder is gone.~~ **Done.** `href(ResourcesFormat(), OAuth2.Authorize(…))`
  for the path, Ktor's `URLBuilder` for the query. All eight tests passed unchanged, including the
  one that pins the whole URL character by character — which is what says the replacement was
  equivalent rather than merely compiling.
- ~~AC: `:crash-client` sends katcher's own `CreateReportParams`, and `CrashReport` is deleted.~~
  **Done, and the copy was already wrong** — see below.
- ~~AC: `SmtpReceiptSender` checks `session.isEncrypted` after `startTls`, and the run against
  Mailpit passes with it.~~ **Done.** Both the send and its negative control pass with the check in
  place.
- ~~AC: every coordinate is a release or a build pinned by metadata~~ **— and it was not, which is
  the finding of this item.** See below; the graph of `:server`, `:auth-client` and `:crash-client`
  now contains no `-SNAPSHOT` at all.
- Anchors: `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `crash-client/src/commonMain/kotlin/io/github/youndie/shashki/crash/CrashReport.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/data/SmtpReceiptSender.kt`

## What it turned out to be

**Two of the three were deletions. The third found that the build was not pinned at all.**

The authorize URL is shildik's `@Resource` and Ktor's `URLBuilder` now; the hand-written
percent-encoder is gone and the eight existing tests passed unchanged, which is the only evidence
worth having that a replacement is equivalent.

**The katcher copy was already wrong.** Deleting it showed why a transcription of somebody else's
wire type is a defect rather than a shortcut: shashki's `Breadcrumb` was
`(message, timestamp: Long, category)` and katcher's is
`(timestamp: LocalDateTime, type, message, data)` — a different shape, a different field name and a
different encoding of the time. Nothing had sent a breadcrumb, so it never fired; the first report
that carried one would have been rejected whole. The type compiled, the tests passed, and it was
wrong. That is the argument for sharing a wire type, demonstrated instead of asserted.

**And the third: `smtpkn`'s fix was published, resolved, and absent from the build.**

The catalog named `0.1.0-20260902.062954-3`, `./gradlew :server:dependencies` showed it, and
`check(session.isEncrypted)` still failed. **A timestamped snapshot pins the root module and not its
platform variants**: the root's own Gradle metadata points the JVM variant at
`smtp-client-jvm:0.1.0-SNAPSHOT`, and that came from a jar the cache had fetched at 02:43, four
hours before the fix. Nothing failed to resolve and nothing warned. The symptom was a test that
passed with `--refresh-dependencies` and failed without it — which is the shape of every
irreproducible build there has ever been.

[B-13](B-13-pin-every-dependency.md) closed Risk 3 partly on this fallback, and the fallback is
advice written for a JVM library. **Every portfolio library here is multiplatform**, so it has a
second coordinate the advice does not reach. A `resolutionStrategy` in `:server` now maps the
variants onto the same builds and the graph carries no `-SNAPSHOT`; the real fix is a release, and
research §1 records the hole so the next person pinning a snapshot knows there are two halves to pin.
