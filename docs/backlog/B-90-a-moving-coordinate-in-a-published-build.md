---
id: B-90
title: "Publishing the repository made a moving snapshot coordinate a defect, and pinning its root pinned half of it"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-90 — Publishing the repository made a moving snapshot coordinate a defect, and pinning its root pinned half of it

[B-13](B-13-pin-every-dependency.md)'s criterion is "a release or a pinned snapshot **before the demo
is published**", and it settled that the guarantee travels with the dependency: whichever item adds
one carries the pinning. [B-14](B-14-receipt-over-smtpkn-jvm.md) did that for smtpkn, twice, and
recorded that a publish can carry two build numbers. [B-47](B-47-driver-onboarding-and-the-object-store.md)
added s3kn, recorded the snapshot in a catalog comment — and did not pin it:

```toml
s3kn = "0.1.0-SNAPSHOT"
```

That was defensible while nobody else could clone this. **The repository went public on 2026-09-03**,
which is the line B-13 draws: a moving coordinate in a public build means two people who clone a week
apart do not compile the same thing, and a republish under the same coordinate changes what everyone
gets with no commit here to show for it.

- AC: no coordinate this build resolves is a moving one, and the artefact that reaches the classpath
  is the one named — not merely the module that names it.
- Anchors: `gradle/libs.versions.toml`, `server/build.gradle.kts`

## What it turned out to be

**Two fixes, because the first one only looked like a fix.** Pinning the catalog entry to the
resolved build is the obvious half:

```toml
s3kn = "0.1.0-20260817.123924-1"
```

Reading the resolved graph rather than the catalog is what showed it was not enough:

```
+--- io.github.youndie:s3-client:0.1.0-20260817.123924-1
|    \--- io.github.youndie:s3-client-jvm:0.1.0-SNAPSHOT
```

The pinned artefact is a Kotlin Multiplatform *root* module, and its published POM asks for the
platform variant at the moving coordinate. So the metadata was pinned and the jar — the thing that
actually reaches the classpath — was not, while the catalog read as though the job were done. Naming
the platform variant beside the root pins both, and the graph now says so:

```
|    \--- io.github.youndie:s3-client-jvm:0.1.0-SNAPSHOT -> 0.1.0-20260817.123924-1
+--- io.github.youndie:s3-client-jvm:0.1.0-20260817.123924-1 (*)
```

**The general shape is the one B-14 already met and named**: a pin is a claim about a *set* of
coordinates, and the set is bigger than the line you wrote. smtpkn's set spanned two build numbers;
s3kn's spans two modules on one number. Both are invisible in the catalog and both are one
`dependencies --configuration runtimeClasspath` away from being visible.

**What this still does not buy** is what B-13 already wrote down: these live on one self-hosted
repository where nothing but convention stops a republish under the same build number. Dependency
verification metadata would catch that; it is still not added, for B-13's stated reasons.
