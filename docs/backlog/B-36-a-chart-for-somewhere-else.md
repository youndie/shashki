---
id: B-36
title: "A chart, and the honest replica count that goes in it"
status: done
priority: P2
size: M
stage: stage-4-elsewhere
blocked_by: [B-35]
---

# B-36 — A chart, and the honest replica count that goes in it

Every other service in this portfolio has one — `charts/katcher`, `charts/shildik` — and this one
has no `charts/` at all. What it has instead is `docker/compose.yaml`, which stands up the services
shashki *talks to* and says in its own first line that it is not a deployment.

- **The chart's real content is the configuration surface, and it is already knowable.** The server
  reads its database from `DatabaseConfig.fromEnv`, its provider from `AuthConfig`'s three variables,
  its graph directory from `RoutingConfig`, and the clients read `globalThis.SHASHKI` for the server,
  the tiles, katcher and the release. A values file that names exactly those and nothing else is the
  documentation this project does not otherwise have; one that names variables the server never
  reads is worse than none.
- **`replicas` cannot be more than one, and the reason is in the code rather than in caution.**
  `DriverIndex` and `InMemoryOfferBoard` are both in-process maps, on purpose — the saga's row is the
  record and these are caches (B-12, B-20). Two replicas are therefore two different sets of online
  drivers, and a driver's position socket lands on one of them while the rider's request for a
  candidate lands on the other. The socket makes it worse: `driverPositionRoutes` holds a connection
  open, so this is not a stateless service that merely happens to cache.
- **That is a finding to write down, not a defect to hide behind a default.** A chart that quietly
  says `replicas: 1` teaches nobody anything; one that says why is the reference doing its job. What
  it costs to lift the limit — a shared index, or partitioning by geography — belongs in the research
  as an option, not in this item as work.
- The rejected alternative is no chart and a compose file promoted to deployment. It works on one
  machine and fails the moment somebody asks where the secrets come from.
- Deliberately **not** covered: an ingress for the tile archive or for katcher. Those are other
  services' deployments and this chart should name their addresses rather than own them.

- AC: `helm lint charts/shashki` passes with the values a real deployment would set, and the rendered
  manifests are read once by a person rather than only by the linter.
- AC: every environment variable the server actually reads has a value in the chart, and every value
  in the chart is read by the server — checked against the code rather than against memory.
- AC: the replica limit is in the chart with its reason beside it, and the reason names the two
  in-memory structures rather than saying "stateful".
- AC: secrets — the database password, the bootstrap token, the katcher key — come from a `Secret`
  and are not values in `values.yaml`.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/db/DatabaseConfig.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/auth/AuthConfig.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/DriverIndex.kt`

## What it turned out to be

**The chart is ordinary and two things in it are not.**

**It refuses more than one replica rather than defaulting to one.** `{{ fail }}` with the reason in
it, because a chart that quietly says `replicas: 1` teaches nobody anything and the next person to
want throughput will simply change the number. The reason is not caution: `GridDriverIndex` and
`InMemoryOfferBoard` are in-process caches, so two pods are two different sets of online drivers, and
a driver's position socket — held open for the length of a shift — lands on one pod while the rider's
candidate query lands on the other. The same fact makes the update strategy `Recreate`: a rolling
update *is* two pods at once.

**And the configuration surface is held to the code by a script rather than by my word.**
`scripts/chart_config.py` reads the variable names out of `server/src/main` and out of the deployment
template and compares them in all three directions. Checked by mutation, one per direction:

| Mutation | What it said |
|---|---|
| drop `SHASHKI_TILES_URL` from the chart | read by `BundleRouting.kt` and not set by the chart |
| add a variable nothing reads | set by the chart and read by nothing |
| rename `SHASHKI_BUNDLES` in the code | exempted and no longer read at all |

The third is the one that matters. Four variables are deliberately absent — the image provides the
graph directory and the bundles, the extract is a build-time input, and the driver's id is the
browser's — and a blanket "ignore what is not in the chart" would have hidden exactly the gap the
script exists for. So each exemption is named with its reason, and an exemption whose variable has
stopped existing is itself a failure.

**Twenty-three variables in the code, nineteen in the chart, four provided by the image.** That the
three numbers add up is the whole of the second criterion, and it is now a line of output rather than
a paragraph somebody wrote once.

**Read once by a person**, as the first criterion asks: the rendered deployment is above in this
session's transcript, and the three refusals were exercised — two replicas, an ingress with no host,
and a deployment with no database each fail with a sentence rather than with a schema error.

**Secrets are secrets.** The database password, katcher's app key and the SMTP password come from
`secretKeyRef`; nothing that is one is a value in `values.yaml`.

**Not covered, and deliberately:** no ingress for the tile archive or for katcher — those are other
services' deployments and this chart names their addresses rather than owning them. No `PodDisruptionBudget`
either: with one replica a budget is a sentence about a situation that cannot arise.
