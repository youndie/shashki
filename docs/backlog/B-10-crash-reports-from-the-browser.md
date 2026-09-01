---
id: B-10
title: "Crash reports from the browser go over katcher's ingest endpoint"
status: open
priority: P2
size: S
stage: stage-3-surface
---

# B-10 — Crash reports from the browser go over katcher's ingest endpoint

The brief has both clients reporting to katcher. Research §1.6 found the katcher client publishes
jvm, four native desktop targets, three iOS targets and mingw — and no browser target at all. The
ingest is documented instead: `POST {serverUrl}/api/reports`.

- **A Ktor call, not a library change.** Adding `wasmJs` to katcher would be modifying a library so
  a consumer can skip writing a request.
- The price is stated rather than discovered: the offline persistence and the build-uuid plumbing the
  Android client gets for free are not free here. What the browser sends is what we choose to send.
- Not covered: the server, which uses the JVM client normally.

- AC: an uncaught exception in either client arrives in katcher with the build identifier attached.
- AC: the katcher coordinate this adds is a release or a CI-numbered publish, not a `-SNAPSHOT`, and a clean checkout on an empty cache still builds. **Handed over from [B-13](B-13-pin-every-dependency.md)**, which closed Risk 3 for the graph as it stood and could not close it for a dependency nobody had added yet — katcher is one of the five the research recorded as pre-release.
- Anchors: `katcher/README.md`, `katcher/client/build.gradle.kts`
