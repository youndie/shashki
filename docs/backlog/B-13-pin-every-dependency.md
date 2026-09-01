---
id: B-13
title: "Every dependency is a release or a pinned snapshot before the demo is published"
status: done
priority: P1
size: S
stage: stage-3-surface
---

# B-13 — Every dependency is a release or a pinned snapshot before the demo is published

Research §1 recorded the versions as read on 2026-09-01: booblik, bochka, s3kn, tracy and smtpkn are
all `-SNAPSHOT`. A reference service that cannot be rebuilt in six months is a screenshot of a
reference service.

- **Pin or release, and re-read the table rather than remembering it.** The version table in research
  §1 is a dated measurement and says so; this item is what turns it into a build guarantee.
- The rejected alternative is dependency locking alone. It pins what resolved, which is right, but it
  does not surface that five libraries are pre-release — and that is the thing a reader of the demo
  should know.
- Not covered: forcing releases upstream. Where a release is not close, the snapshot is pinned by
  build metadata and named in the README.

- ~~AC: a clean checkout on a machine with an empty cache builds.~~ **Done, 2026-09-02.**
  `git archive HEAD` extracted outside the working copy, built with a fresh `GRADLE_USER_HOME`:
  `BUILD SUCCESSFUL in 2m 30s`, 98 tasks all executed, 1.1 GB fetched — the Gradle distribution and
  the JDK 25 toolchain included, because an empty home has neither.
- ~~AC: research §1's version table re-verified against what actually resolves, and §3 Risk 3 closed
  with the outcome.~~ **Done, 2026-09-02.** No `-SNAPSHOT` and no dynamic version in 18 940 lines of
  resolved graph across the four modules, nor on the plugin classpath. Risk 3 is closed for the graph
  as it stands and explicitly *not* for the three dependencies nobody has added yet.

**Amended after B-11.** The server now pins `petich 0.1.0.10` — a CI-numbered build on the
`/snapshots` line, like kvadrant's `0.2.0` — plus Exposed 1.4.0 (petich's own pin), Hikari, the
Postgres driver, Flyway and Testcontainers at the numbers the neighbouring service uses. The
catalog is the fact; this item re-reads it, not the research table.

## What it turned out to be

**The risk was not where the table said, and closing it where the table said would have been the
mistake.**

The version table records five libraries as `-SNAPSHOT`. Reading the resolved graph rather than the
table shows that **none of the five is a dependency of shashki** — nor are katcher, telek, kompot or
shildik. What resolves from the portfolio is kvadrant-core 0.2.0, viddik 0.3.3.19 and petich
0.1.0.10, all CI-numbered publishes. So there was nothing here to pin, and a tick against Risk 3
would have recorded a mitigation for a situation that has not arrived.

It arrives with [B-07](B-07-serve-pmtiles-from-bochka.md), [B-10](B-10-crash-reports-from-the-browser.md)
and [B-14](B-14-receipt-over-smtpkn-jvm.md) — bochka, katcher, smtpkn — so each of those now carries
the pinning as an acceptance criterion of its own. The guarantee travels with the dependency instead
of being claimed for it a quarter early.

**The empty-cache build was worth running for a second reason.** It passed, which is the criterion —
but it also printed two deprecation warnings that five hundred incremental builds had never shown,
because a warm configuration cache never re-evaluates the build script. One of them was mine, from
the day before: `compose.uiTest` is deprecated in Compose 1.12 in favour of naming the artefact.
Naming it means writing a version by hand, and a test harness a minor behind its runtime is exactly
the `NoSuchMethodError` research §1.2 describes — so the number is checked against
`wip.versions.composeMultiplatform` at configuration time, and the control is that setting it to
1.11.1 fails the build with both numbers in the message.

**What pinning does not buy, and is not pretended to.** These coordinates live on one self-hosted
repository, on its `/snapshots` line, where nothing but convention stops a republish under the same
number. Gradle's dependency verification metadata would catch that; dependency locking would not,
since it pins coordinates this build already pins by hand. Neither is added: the checksum file is a
second thing to regenerate on every change, and the larger exposure is that the host is single-homed,
which no checksum survives. Both are written into the README and the research so the next reader
weighs them instead of assuming this item covered them.
