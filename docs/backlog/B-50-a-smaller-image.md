---
id: B-50
title: "A smaller image: the 569 MB that B-35 measured and did not touch"
status: open
priority: P3
size: S
stage: stage-4-elsewhere
---

# B-50 — A smaller image: the 569 MB that B-35 measured and did not touch

B-35 closed with the number and the split: 569 MB, of which the JRE base is 104, the application 41,
both bundles 31, the graph 14 — and "the rest is the base image's own layers", which is roughly 380 MB
of an operating system the server does not call. It said a jlink runtime or a distroless base is a
separate argument. This is where that argument is had, and it is P3 because nothing about the demo
depends on it.

- **Measure before choosing.** `jlink` with the modules the application actually loads, against a
  distroless Java base, against the current image — three numbers and three start-up times, on the
  same container runtime B-35 measured on. The choice is whichever is smaller *without* moving the
  start-up number B-35 wrote down.
- **GraphHopper is the constraint.** It is the one native-adjacent thing in the process (its file lock,
  its memory-mapped graph), and B-35's fourth criterion — a build and runtime image paired by their C
  library — is the reason a distroless base is not automatically safe. Whatever is chosen, the first
  request after start is `POST /api/routes`, because that is the call that would fail.
- The rejected alternative is trimming layers by hand. It produces a number nobody can reproduce.
- Deliberately **not** covered: the bundles' 31 MB. The Compose runtime is what it is, and D10 already
  named the one saving available.

- AC: the image is measured three ways and the chosen one is written down with its size and its
  start-up time beside B-35's.
- AC: `docker run` of the chosen image answers `/health` and then `POST /api/routes` across the city,
  on the same host B-35 used.
- Anchors: `server/build.gradle.kts`, `docker/`
