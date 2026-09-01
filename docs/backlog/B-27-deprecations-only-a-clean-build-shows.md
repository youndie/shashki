---
id: B-27
title: "The deprecations only a clean configuration shows"
status: open
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

- AC: `./gradlew check` on a fresh `GRADLE_USER_HOME` prints no deprecation warning from this
  repository's own build scripts.
- AC: the version check that B-13 added covers every Compose artefact named by hand, not just
  `ui-test`, and the control is that changing one of them fails the build.
- Anchors: `shared-ui/build.gradle.kts`, `gradle/libs.versions.toml`
