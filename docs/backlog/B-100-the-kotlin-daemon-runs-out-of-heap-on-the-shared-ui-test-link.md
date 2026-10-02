---
id: B-100
title: "The Kotlin daemon runs out of heap on the shared-ui test link in a third of cold CI runs"
status: done
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

## What was measured — 2026-10-02, hosted `ubuntu-latest` runner (4 CPUs, 15 989 MB, no cgroup limit)

The probes were a step of `check.yaml` on this item's branch: a GC log for every JVM through
`JAVA_TOOL_OPTIONS` (so no Kotlin property changed to get it), a two-second sampler of
`MemAvailable` and of each JVM's resident size by role, the Kotlin daemons' own logs, and
`--no-build-cache`, because a branch restores `main`'s cache, in which the links are already stored —
the first probe run hit it and executed none of them. Raw files: `docs/research/measurements-2026-10-02/b-100/`.

**The lever, read from the process.** Unset, the daemon runs with `-Xmx3g -XX:MaxMetaspaceSize=768m
-XX:ReservedCodeCacheSize=320m -XX:+UseParallelGC`: the first two inherited from
`org.gradle.jvmargs`, the code cache a default, the collector added by the plugin. With
`kotlin.daemon.jvmargs=-Xmx<N>` only `-Xmx` changes; the metaspace limit is still inherited.

**The mechanism.** A build starts **two** Kotlin daemons with identical arguments, one per run-files
directory (`~/.kotlin/daemon` and `~/.local/share/kotlin/daemon`) — every run, all six. The first
takes the JVM compilations and some klibs and never holds more than 0.43 GB. The second gets the
rider and driver klibs and **all three wasm test links**, which run in it at once: `:driver` and
`:rider` start together and `:shared-ui` joins while they are still going. In the failures the heap
after full collections climbed to the cap and stayed there for minutes, until one link died — the
`:shared-ui` link once, the `:rider` link twice — and the other two finished within seconds of it.
So the task named in the error is whichever of three was unlucky; none of them is the problem alone.

| Arm (cold build) | Run | Result | Links daemon: max heap after a full collection | Full GCs, GC time | Lowest `MemAvailable` |
|---|---|---|---|---|---|
| today, 3g inherited | 36980747220 | **failed**, `:shared-ui` | 2 751 MB of 2 904 usable | 102, 225 s of 351 s | 7 075 MB |
| today, 3g inherited | 36980753549 | **failed**, `:rider` | 2 790 MB | 75, 159 s of 301 s | 6 984 MB |
| today, 3g inherited | 36980747220 attempt 2 | **failed**, `:rider` | 2 750 MB | 91, 197 s of 324 s | 7 078 MB |
| today, 3g inherited | 36980753549 attempt 2 | passed | 2 791 MB | 59, 111 s of 214 s | 5 887 MB |
| measuring, 5g, forced GC | 36982124134 | passed | 2 484 MB | 25, 44 s of 142 s | 4 522 MB |
| measuring, 5g, forced GC | 36982130089 | passed | **3 060 MB** | 32, 68 s of 192 s | 4 305 MB |

Same runner type, separate runs; the baseline rows have no forced collections (they were not needed —
at the cap a full collection happens every two seconds). The probe build fails more often than an
ordinary pull request (3 of 4 against 4 of 11) because nothing at all comes from the cache.

**Each link alone**, in a fresh daemon at 3g with its incremental cache removed, sampled the same way
(two runs): `:shared-ui` 716–914 MB, `:rider` 789–859 MB, `:driver` 899–902 MB. Three of them together
need about three times one, which is what the 3 GB heap did not have.

**The control**, against the amended unit: the `:shared-ui` link alone under 0.8× its own live set
failed both times — 731 MB (`GC overhead limit exceeded`) and 572 MB (`Java heap space`). Against the
first unit it had passed at 900 MB; that is what amendment 1 is about.

**The rule, applied.** The largest live set in any cold run is 3 060 MB; 1.5× is 4 590 MB, so **5g**.
The memory it adds was measured rather than estimated: at the peak of the 5g runs the links daemon
held 4.9–5.1 GB resident against 3.6–3.7 GB at 3g, the other daemon 2.4–2.5 GB in both (its live set
stays under 0.43 GB, so a higher cap gives it nothing to grow into), and the lowest `MemAvailable`
was 4 305 MB — inside the 5 887 MB baseline minimum with more than 2 GB to spare.

**Declared before the verification runs:** if any of them shows a live set of 3 413 MB or more (two
thirds of 5g), the item does not close at 5g; the next step goes to the owner with the numbers.

## The verification — six cold builds at 5g

`kotlin.daemon.jvmargs=-Xmx5g` in `gradle.properties`, the probe otherwise as in the measuring arm
(cold, forced collections). Two runs, each run three times.

| Run | Result | Links daemon: max heap after a full collection | Lowest `MemAvailable` |
|---|---|---|---|
| 36984514289 | passed | not kept: re-running a workflow drops the previous attempt's artifact | — |
| 36984518756 | passed | 2 795 MB | 4 288 MB |
| 36984514289 attempt 2 | passed | 2 743 MB | 4 504 MB |
| 36984518756 attempt 2 | passed | 2 713 MB | 3 709 MB |
| 36984514289 attempt 3 | passed | 2 706 MB | 3 718 MB |
| 36984518756 attempt 3 | passed | 2 689 MB | 4 456 MB |

Every kept run is under 3 413 MB, two thirds of the cap, and both daemons carry `-Xmx5g` on their
command lines. Same runner type, separate runs. The lowest `MemAvailable`, 3.7 GB, came with Chrome
at 4.1 GB resident during the browser suites — Chrome's figure counts shared pages once per process,
so it overstates.

**How much six greens prove.** Not much alone. If the probe build still failed at its baseline rate
(3 of 4), six passes in a row would come up about once in four thousand; at an ordinary pull
request's rate (4 of 11), about once in fifteen — and both rates rest on few runs. The argument is
the headroom: the same three links that stalled at 2.75–2.79 GB in a 3 GB heap peaked at 2.69–2.80 GB
here with 5 GB available, and two thirds of the cap was the line declared before these runs.

**What this leaves.** A warm build — `main`'s cache, every link stored — never links at all, so a
green `main` after the merge says nothing either way; the next cold build is the next real reading.
Three things were seen and not changed, because the rule did not ask for them: the plugin starting a
second daemon with the same arguments (each daemon gets the cap; the second stays under 0.43 GB live
and 2.0–2.5 GB resident at either cap); the Gradle daemon's 3 GB heap, whose live set never passed
0.3 GB; and serialising the three links, which would make the overlap impossible instead of
affordable.
