---
id: B-100
title: "The Kotlin daemon runs out of heap on the shared-ui test link in a third of cold CI runs"
status: wip
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-100 — The Kotlin daemon runs out of heap on the shared-ui test link in a third of cold CI runs

Since the stack moved to Kotlin 2.4.20 (#33), `./gradlew check` on CI fails now and then in
`:shared-ui:compileTestDevelopmentExecutableKotlinWasmJs` with an `OutOfMemoryError` — `GC overhead
limit exceeded` three times, `Java heap space` once — and the Kotlin Gradle plugin's advice to raise
`kotlin.daemon.jvmargs`. Counted over every `gradle` job of 2026-10-01 and 2026-10-02, reruns
included: **4 failures in 11 cold runs** (every wasm test link executed), **0 in 7 warm ones** (the
links came from the build cache). A rerun usually passes, which is why it has gone unexamined.

`gradle.properties` sets `org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m` and no
`kotlin.daemon.jvmargs`. Kotlin's documentation says the daemon then inherits `-Xmx`,
`-XX:MaxMetaspaceSize` and `-XX:ReservedCodeCacheSize` from the Gradle daemon, and that a running
daemon whose heap is large enough is reused for every compilation — so the JVM compiles, the wasm
klibs and the three wasm test links of one build would share one 3 GB heap, four workers at a time on
a hosted runner. That is the hypothesis. This item exists so that the setting is chosen from a number,
not raised until the failure stops.

## Fixed before the first number

- **The unit:** the Kotlin daemon's heap after collection — its live set — read from the daemon's own
  GC log; the maximum over a run. On the machine side: the lowest `MemAvailable` on the runner, with
  the resident size of each JVM beside it.
- **The lever, read from the subject:** the daemon's `-Xmx` as its command line on the runner shows
  it, not as the documentation says it should be.
- **Two readings per run:** the whole cold `check` (what fails), and the shared-ui test link alone in
  a fresh daemon — the daemon stopped, the one task run with `--rerun` — which is what that task needs
  without neighbours.
- **The positive control:** the isolated link with the daemon capped at 0.8× its own measured live
  set must fail with the same `OutOfMemoryError`. If it passes, the live set does not measure what the
  task needs and the rule below is void.
- **The rule:** the new cap is at least 1.5× the largest live set measured in any cold run, in whole
  gigabytes; and what it adds over today's 3 GB must fit in the lowest `MemAvailable` seen in the
  baseline with 2 GB to spare. If it does not fit, lower the build's parallelism on CI instead and
  say what that costs in minutes.
- **The verification:** at least four cold runs with the setting, all green, each with its live-set
  maximum under two thirds of the cap. Four greens alone are weak — at the measured failure rate
  about a fifth of unfixed builds would pass four in a row, (7/11)⁴ ≈ 0.16 — so the headroom is the
  argument and the greens are the check that nothing else broke.
- Not covered: making the link itself cheaper (fewer tests in one executable, a smaller test graph).
  That is its own item if the number says the link alone is the problem.

- Anchors: `gradle.properties`, `.github/workflows/check.yaml`

## Amendment 1 — 2026-10-02, after two cold runs at today's setting and before any other arm

- **The control failed to fail, so the unit changes.** Capped at 900 MB, the shared-ui link alone
  passed, its heap after full collections at most 558 MB. The 1 129–1 156 MB the rule was going to
  scale came from young collections: the daemon runs the Parallel collector (`-XX:+UseParallelGC` on
  its command line, put there by the Kotlin Gradle plugin), and a young pause reports the old
  generation with its dead objects in it. The unit is now **the heap after a full collection**. The
  measuring arm forces one in each Kotlin daemon every six seconds (`jcmd <pid> GC.run`) so that the
  peak is sampled rather than waited for; forced collections slow the compilations and lengthen their
  overlap, so the figure can only err high. The control is run again against the new unit.
- To find the peak the daemon must survive it: the measuring arm runs the cold build with
  `-Pkotlin.daemon.jvmargs=-Xmx5g`. The rule, its thresholds and the verification are unchanged.
