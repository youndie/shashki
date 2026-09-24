---
id: B-98
title: "What one saga did is written nowhere this server can read it"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
blocked_by: []
---

# B-98 — the saga trace, into the log

Every signal this server has about a saga is keyed by saga type or by request: metrik counts
routes, tracy carries spans per member, `RefusingMetrics` counts per type, and the row keeps the
latest position only. The question "what did ride X go through" is answered by reading code.

petich now has a per-saga event hook — its B-58, `PetichTracer` — and a sink that writes one line
per event, `LinePetichTracer` (its B-60). petich's B-60 is the reason this item exists: its research
asks whether **two weeks of reading these lines** answers anything a counting test double had not,
and shashki and konekt are the two places that can say. This item is the wiring; the two weeks are
petich's to count.

- **One line per event through slf4j**, logger `shashki.saga`, prefix `petich.trace` — one grep away
  from every other log line, no new dependency, no collector.
- **The replica is `HOSTNAME`** (`ObservabilityConfig.replica`): the pod name under Kubernetes, the
  container id under Docker, `local` here. The engine has none to give; the sink stamps it.
- **`sagaEngine` takes the tracer as a defaulted parameter**, because every saga test builds its
  engine there and only one asserts on the trace. `RideModule` is the one place that passes it.
- Rejected: a sink written here. petich ships the line format and pins it with a test, so a query
  written against konekt's log works against this one.
- Not covered: sending the trace to tracy. That is petich's B-61, and it is gated on what these two
  weeks show.

## Acceptance

- `petich = "0.4.0.112"`, the build compiles, and `./gradlew check` is green.
- A test runs a ride through the production factory with a `LinePetichTracer` and reads back where
  it waited and how it ended.
- The server's own log carries `petich.trace` lines when a saga runs.

## Findings

**No source change beyond the wiring.** `0.4.0.106` → `0.4.0.112` brings petich B-58, B-59, B-65
and B-60, and no column. B-65 changes what a saga read mid-pass says — `PROCESSING` where it said
`DRAFT` — and `PetichRideRepository` already maps the two to the same thing, so nothing here moved.

**Verified through the real path, not only the new test.** `./gradlew check` on the Linux box against
a real Postgres: green, `OrderSagaTest` 8/8 (result file read, the new test among them). And the log
of that run carries the lines from **`ApplicationTest`**, which boots the real Koin graph — a
settlement saga written out event by event by the server itself:

```
INFO  shashki.saga - petich.trace ts=… replica=local saga=…:settlement type=settlement event=PassStarted attempt=1 status=PROCESSING phase=ENRICHMENT index=0
INFO  shashki.saga - petich.trace ts=… replica=local saga=…:settlement type=settlement event=MemberEntered phase=EXECUTION index=0 key=capture
INFO  shashki.saga - petich.trace ts=… replica=local saga=…:settlement type=settlement event=Finished status=COMPLETED
```

**Found on the way: [B-99](B-99-refusing-metrics-no-longer-refuses.md).** `RefusingMetrics` throws
from `onDroppedEvents` on purpose, and since petich B-52 — already in the `0.4.0.106` this server
was on — the engine wraps metrics in a guard that swallows exactly that throw.
