---
id: B-50
title: "A smaller image: the 569 MB that B-35 measured and did not touch"
status: done
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

## What it turned out to be

**Three ways, measured on the same box and the same runtime, with a route as the first request.**

| Base | Image | To healthy | `POST /api/routes` |
|---|---|---|---|
| Temurin's JRE on Ubuntu — what shipped | 577 MB | 2 025 ms | 200 |
| a `jlink` runtime on `ubuntu:noble` | 371 MB | 2 025 ms | 200 |
| the same runtime on `alpine:3.21` | **263 MB** | 1 999 / 2 018 / 2 025 ms | 200 |

`docker/measure-bases.sh` is that table, runnable. The chosen one is Alpine: **264 MB against 577,
and a start-up inside B-35's own 1.5–3.7 s** — three restarts, none of them slower than what shipped.

**Distroless never became a candidate, and the reason is the item's own second bullet.** Its Java
images stop at 21, so the bytecode this server compiles would not run on one; its `base-debian12`
would run a `jlink` runtime, but only one linked against Debian 12's glibc — and the JDK that links
it is Ubuntu's. Pairing a build image and a runtime image by their C library is what makes the third
row Alpine on *both* sides rather than a glibc runtime dropped onto musl, which fails at its first
native call rather than at build time.

**The module list is `jdeps`', not a hand-written `--add-modules`.** A list somebody writes is right
until a library reaches for `java.sql` on a path no test exercised, and `jlink` cannot warn about
what it left out — the symptom is a `NoClassDefFoundError` in production. Three modules are named
after it because nothing references them statically: TLS curves, `sun.misc.Unsafe`, and JMX for
metrik.

**And the number in B-35 was two numbers.** `docker images` reports 577 MB for what shipped;
`docker image inspect` reports 161 MB of content on top of shared base layers. Both are in the
script's output, because comparing one against the other is how a 569 MB image becomes a 161 MB
claim.

- AC 1: three ways, above, and the chosen one written down with its size and its start-up beside
  B-35's.
- AC 2: on the stand — `/health` 200, `POST /api/routes` 200 across the city, both bundles served,
  the graph open in 271 ms. The one thing the change did *not* touch is what B-35 measured.
