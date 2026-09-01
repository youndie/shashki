---
id: B-13
title: "Every dependency is a release or a pinned snapshot before the demo is published"
status: open
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

- AC: a clean checkout on a machine with an empty cache builds.
- AC: research §1's version table re-verified against what actually resolves, and §3 Risk 3 closed
  with the outcome.

**Amended after B-11.** The server now pins `petich 0.1.0.10` — a CI-numbered build on the
`/snapshots` line, like kvadrant's `0.2.0` — plus Exposed 1.4.0 (petich's own pin), Hikari, the
Postgres driver, Flyway and Testcontainers at the numbers the neighbouring service uses. The
catalog is the fact; this item re-reads it, not the research table.
