---
id: B-96
title: "petich is pinned one commit before the handler B-97 needs, and nine behind"
status: wip
priority: P2
size: M
stage: stage-6-what-running-it-said
blocked_by: []
---

# B-96 — the pin, and what is on the other side of it

`gradle/libs.versions.toml` pins `petich = "0.4.0.97"`. That snapshot is petich's commit `12d9028`,
which is **one commit before `AnnouncementFailureHandler` exists** — so B-97 is not a matter of
passing an argument that was never passed, it is a type this build cannot name.

`0.4.0.106` is published on `reposilite.kotlin.website/snapshots` (checked: `petich-core` and
`petich-postgres` both answer 200 for their `.module`). Between the two are nine commits:

| petich item | what it changed | does shashki see it |
|---|---|---|
| B-49 | `AnnouncementFailureHandler`, and a failed announcement leaves the database | needed by B-97 |
| B-51 | a fault outside a member rolls the saga back instead of writing `FAILED` and undoing nothing | behaviour, and it is the good direction |
| B-52 | application code — metrics, both failure handlers — cannot decide a saga's fate; an announcement gets its own deadline | behaviour |
| B-53 | a parked member is undone from where it parked | behaviour |
| B-54 | a rollback carries what it will end as; **two new columns** | **schema** |
| B-55 | the sweeper stops counting a refusal as a rescue; the two queues fail independently | behaviour, and it touches B-95's subject |
| B-56, B-57 | docs, and `requireAnnouncementFailureHandler` | needed by B-97 |

**B-54 is the one with a cost.** It adds `compensating_from_index` and `compensating_towards` to the
saga table. petich ships no migrations; the `ALTER`s are in its README's upgrade table, both columns
are nullable, and both are a catalogue change rather than a table rewrite. This repository owns its
own migrations, so that is a new `V<n>__` file, not a hope.

Everything else is additive: no signature removed, no behaviour this application asked for reversed.
The compiler is the honest oracle for the first claim and the suite is the oracle for the second,
which is why this is one item rather than a sentence in B-97.

## Acceptance

- `petich = "0.4.0.106"` and the build compiles with no source change — or, where a source change is
  needed, this file says which and why.
- A migration adds both of B-54's columns, and the saga suite passes against a database that has run
  it. A saga interrupted mid-rollback under the old schema still finishes the old way; that is
  petich's documented behaviour and is not this item's to change.
- `./gradlew check` green where it runs, native half included.
- The saga tests that exist for a dead process (B-93's) still pass — B-51 and B-55 both moved code
  those exercise.
