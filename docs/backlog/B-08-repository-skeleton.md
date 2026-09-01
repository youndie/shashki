---
id: B-08
title: "The repository skeleton: modules, targets, versions and the check target"
status: open
priority: P0
size: M
stage: stage-1-skeleton
---

# B-08 — The repository skeleton: modules, targets, versions and the check target

Nothing is built yet. The brief points at the reference repository layout — shared / clients /
server — and research §1 fixed the versions the whole stack agrees on: Kotlin 2.4.10 and Compose
Multiplatform 1.12.0, which is also what `org.maplibre.compose` builds against.

- **Targets are declared, not inherited.** Research §1.6 found four libraries whose target sets do
  not match what the brief assumes; each module states what it builds for and a comment says why.
- The rejected alternative is copying a settings file. The target list is exactly the part that has
  to be re-derived, and it is the part a copy silently gets wrong.
- Not covered: the wasm client targets, which wait on B-01.

- AC: `./gradlew check` and `make check` both run and both are what CI runs.
- AC: one version catalog, with the Reposilite repositories filtered by group as in the neighbouring
  projects.
- AC: `shared-ui` builds a desktop target, because viddik's capture engine is JVM-only and the
  component browser is the design acceptance.
