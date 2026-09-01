---
id: B-22
title: "kvadrant-ui: publish 0.2.0 with the two hooks, so B-03 stops waiting on a merge"
status: done
priority: P0
size: XS
stage: stage-0-unknowns
---

# B-22 — kvadrant-ui: publish 0.2.0 with the two hooks, so B-03 stops waiting on a merge

[B-18](B-18-kvadrant-overridable-on-accent.md) and [B-19](B-19-kvadrant-app-bar-tokens.md) are
closed: `KvadrantColors(onAccent = …)` and the five `appBar*` fields on `KvadrantMetrics` are on
kvadrant-ui's `main`. They are in no artefact. Checked on 2026-09-01 (research D3): Reposilite holds
`kvadrant-core 0.1.0` from before the merge, `/releases` is empty, the only tag is `v0.1.0`, and the
CHANGELOG files both under `Unreleased`. [B-03](B-03-shashki-foundation-module.md) is `wip` with its
last acceptance criterion — black ink through the library's parameter — blocked on exactly this, and
`skeleton_themes` records white on purpose until it lands. The blocker is a publication the owner of
both repositories controls.

- **0.2.0, not a snapshot.** Two breaking changes on `data class`es under ABI validation are a minor
  bump by the library's own versioning; a CI-numbered snapshot would work for shashki and leave the
  CHANGELOG saying `Unreleased` about a change a consumer already depends on.
- The rejected alternative is a shashki-local `onAccent` constant shadowing the library until the
  release. B-03 refuses it in its own acceptance criteria, and it is the one workaround that would
  outlive the reason for it.
- Not covered: anything else waiting in kvadrant's `Unreleased`. If more is there, it ships in the
  same version; this item does not gate on it.

- AC: `io.github.youndie:kvadrant-core:0.2.0` resolves from Reposilite `/releases`, tag `v0.2.0`
  exists, and the CHANGELOG moves both entries under it.
- AC: `gradle/libs.versions.toml` pins `kvadrant = "0.2.0"`, `skeleton_themes` re-records with black
  ink on both accents, and B-03 closes.
- Anchors: `gradle/libs.versions.toml`, `kvadrant-ui/CHANGELOG.md`

## What it turned out to be

Published the same day. `io.github.youndie:kvadrant-core:0.2.0` resolves, its `desktop` variant is
there, the CHANGELOG on `main` opens with `## 0.2.0 — 2026-09-01`, and shashki pins it — B-03 closed
on it within the hour.

**Two of this item's own words were wrong, and they stay here corrected rather than deleted.**

- ~~AC: resolves from Reposilite `/releases`~~ — it resolves from **`/snapshots`**, which is where
  0.1.0 already lived and where `settings.gradle.kts` already pointed; `/releases` under this group is
  a 404 for 0.2.0 exactly as it was for 0.1.0. The item assumed a release line the library never used.
  Nothing to change on the consumer side, and one less thing to believe about a host.
- ~~tag `v0.2.0` exists~~ — **it does not**; the only tag is still `v0.1.0`. The artefact and the
  CHANGELOG say 0.2.0 exists; the repository does not. That is kvadrant's to settle (its B-46 is where
  "what spending a version number costs" was written), and it does not gate shashki — a pin resolves
  against Reposilite, not against a tag.
- ~~AC: `gradle/libs.versions.toml` pins `kvadrant = "0.2.0"`, `skeleton_themes` re-records with
  black ink on both accents, and B-03 closes.~~ Done.
