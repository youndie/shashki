---
id: B-08
title: "The repository skeleton: modules, targets, versions and the check target"
status: done
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

## What it turned out to be

Three modules, and the count is an argument rather than a default. `:protocol` (KMP, `jvm` only),
`:server` (Ktor CIO on the JVM, `application`), `:shared-ui` (Compose Multiplatform, `jvm("desktop")`
only). The brief's five server boundaries — rider-api, driver-api, dispatch, pricing, billing — are
**packages**, not Gradle modules: splitting an empty server into five of them would be inventing
boundaries before the domain has any, and the research already says the microservice story here is
told by the saga and the broker rather than by deployments.

- ~~AC: `./gradlew check` and `make check` both run and both are what CI runs.~~ Both run and both
  are green. CI carries both as **separate jobs**, because they fail for unrelated reasons. One half
  of this is unverifiable today and says so in the workflow: `runs-on` follows the repository's
  visibility, this repository has no remote yet, and both wrong answers are silent — an unpaid
  private account never starts the job, a self-hosted label with no runner parks it in `queued`.
- ~~AC: one version catalog, with the Reposilite repositories filtered by group.~~ Two, and that is
  the shape the portfolio already uses: the shared `wip` catalog for what several repositories must
  agree on, and this repository's `libs` for what it decides alone. **Two group filters and not
  one**, which was found rather than assumed: kvadrant publishes as `io.github.youndie` and viddik as
  `ru.workinprogress`, so the conventions' own declaration reaches only the second. And kvadrant-core
  0.1.0 is on `/snapshots`, not `/releases` — `/releases/io/github/youndie` is a 404.
- ~~AC: `shared-ui` builds a desktop target.~~ It does, and a fixture proves it. A viddik plugin with
  no fixture generates no registry, records no golden and passes with every task reading `NO-SOURCE`
  — indistinguishable from success. `skeleton_themes` is 390 × 844, holds both accents as filled
  surfaces, and was **looked at** rather than merely verified: the first recording had an unpainted
  third and half-width bands, which `viddikVerify` was perfectly happy with.

**The golden is recorded and nothing in `check` compares it.** `verifyOnCheck` is off, which is
viddik's default and the honest setting here: whether these goldens mean the same thing on another
machine is [B-02](B-02-measure-golden-host-independence.md)'s measurement, and switching the
comparison on before it is answered would either redden every run on CI or pass for a reason nobody
checked.

**What the fixture records is the disagreement, on purpose.** The ink on both accent surfaces is
white — `KvadrantColors.onAccent` reproducing Metro faithfully at 2.90:1 on cyan and 2.11:1 on amber
— where the kit asks for black. The parameter that lets a caller say otherwise landed in kvadrant-ui
B-48 and is unreleased, so 0.1.0 cannot express it yet. The picture makes the number legible:
white on amber is visibly hard to read. [B-03](B-03-shashki-foundation-module.md) is where the golden
starts recording what the kit asks for.
