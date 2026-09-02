---
id: B-27
title: "The deprecations only a clean configuration shows"
status: done
priority: P2
size: XS
stage: stage-1-skeleton
---

# B-27 — The deprecations only a clean configuration shows

[B-13](B-13-pin-every-dependency.md)'s empty-cache build printed six deprecation warnings that no
incremental build had ever shown, because a warm configuration cache does not re-evaluate the build
script. One of them was fixed there because it had been introduced the day before; these five are
older and are their own item rather than a passenger in someone else's commit.

- **They are two deprecations, not six warnings.** Compose 1.12 deprecates the `compose.runtime`,
  `compose.foundation` and `compose.ui` accessors in favour of naming the artefacts; Gradle 9.6
  deprecates `val desktopTest by getting` in favour of `getByName`.
- **The Compose half costs something and the item should say so.** Naming the artefacts means
  writing their version by hand, and a Compose UI a minor away from the plugin's own is the
  `NoSuchMethodError` research §1.2 describes. `shared-ui/build.gradle.kts` already has the guard
  that B-13 added for `ui-test`; these ride the same version reference and the same check, or the
  fix trades a warning for a class of runtime failure.
- **The reason this is worth doing rather than muting** is that both are scheduled removals. A
  warning in a build script becomes an error at an upgrade nobody plans around, and the upgrade that
  finds it is the one where something else is also broken.
- Deliberately **not** covered: making the clean-configuration build a routine gate. That is a
  different question — it costs minutes on every run — and the honest answer may be that CI does it
  once a week rather than that everyone does it always.

- ~~AC: `./gradlew check` on a fresh `GRADLE_USER_HOME` prints no deprecation warning from this
  repository's own build scripts.~~ **Done, 2026-09-02: zero warnings of any kind**, and `check`
  green, on a Gradle home created for the run and deleted after it.
- ~~AC: the version check that B-13 added covers every Compose artefact named by hand, not just
  `ui-test`, and the control is that changing one of them fails the build.~~ **Done, and by group
  rather than by alias.** The check walks the catalog for anything under `org.jetbrains.compose` and
  compares it with the version the plugin applies, so an artefact added tomorrow is covered without
  anybody extending a list. Control: dropping the reference to 1.11.1 fails with
  `Compose is 1.12.0 but 4 artefact(s) are pinned elsewhere`, naming all four.
- Anchors: `shared-ui/build.gradle.kts`, `gradle/libs.versions.toml`

## What it turned out to be

**Two deprecations, and the reason they were invisible is worth more than either.**

The Compose accessors and Gradle's `by getting` are both replaced now. What took the time was
understanding why B-13's empty-cache build was the only thing that ever showed them: these are
**script-compilation** warnings, and Gradle caches the compiled build script inside
`GRADLE_USER_HOME`. Not the configuration cache — `--no-configuration-cache --rerun-tasks` still
prints nothing, because the script is not recompiled. Only a Gradle home that has never seen this
script produces them, which is once per machine, on a day nobody is looking for warnings.

**The guard is by group, not by name.** B-13's version check named `ui-test` because `ui-test` was
the only Compose artefact written by hand; this item added three more, and a check that grew a line
per artefact would be an inventory somebody has to remember to extend. Walking the catalog for
`org.jetbrains.compose` covers the next one for free — and it carries its own vacuity guard, because
a check over an empty list passes for ever: if these ever go back behind an accessor, the build says
so rather than going quiet.

**One thing went wrong in the doing and is worth recording.** A scripted edit to the build file
matched a shorter string than intended and duplicated the block across 150 000 lines; the control run
that followed failed, and it failed with a *script compilation error* rather than with the guard. A
control that fails for the wrong reason looks exactly like a control that works. `git checkout` on
the one file, then edits with asserted match counts, then the control again — which then named all
four artefacts, which is what a real failure looks like.
