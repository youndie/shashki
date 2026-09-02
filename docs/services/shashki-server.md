---
id: shashki-server
title: shashki server
type: service
repo_url: https://github.com/youndie/shashki
module: server
tech_stack: [Kotlin, Ktor, Exposed, PostgreSQL, petich, GraphHopper, booblik, JVM 25]
owner: unassigned
depends_on:
  - PostgreSQL
  - booblik
  - shildik
  - bochka
  - katcher
  - an SMTP relay
publishes:
  - shashki/server (a local image; nothing is pushed)
  - ride-events (a booblik topic)
---

# shashki server

## 1. Responsibility

One process that owns everything about a ride: what it costs, who is asked to drive it, what happened
to it, and what was charged for it. It also serves the two browser bundles, which is a decision about
the demo rather than about the architecture — [§5](#5-infrastructure-and-deploy) says what that costs.

**What it deliberately does not do.** It is not a tile server: the basemap is a static pmtiles archive
somewhere else and the browser fetches it over ranged HTTP ([D12](../research/research-architecture.md)).
It is not an identity provider: tokens come from shildik and are verified with shildik's own
validator, because a second implementation of "is this signature ours" is the last thing a service
should own. It is not five services: the rider, driver, dispatch, pricing and billing boundaries are
packages, and `server/build.gradle.kts` says why they are not modules.

## 2. API contracts

* **Contracts:** `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/` — the
  `@Resource` classes and the DTOs, used by the server to route and by both clients to build URLs.
  There is no generated schema; the contract classes are the truth and are compiled into both sides.
* **Auth tiers:** the table in [endpoint-rides](../api/endpoint-rides.md#routes--all-of-them-no-exceptions)
  and its three siblings. Every route's tier is also stated in the KDoc where it is declared.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server/src/main/kotlin/io/github/youndie/shashki/server/Application.kt` | the module, every route's mount point, the error mapping, the sweeper and the relay |
| `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/RideModule.kt` | the whole dependency graph |
| `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSteps.kt` | the order saga, one class per phase |
| `server/src/main/kotlin/io/github/youndie/shashki/server/feature/settlement/saga/SettlementSteps.kt` | the settlement saga |
| `server/src/main/resources/db/migration/` | migrations — four tables, two of them petich's |
| `docker/compose.yaml` | the local stand: this server and everything it talks to |

## 3. How it is built

**Two sagas and a stretch of no saga**, which is the shape of the whole service and is
[research §1.4c](../research/research-architecture.md). The *order* saga runs petich's five phases
once, `REQUESTED → ASSIGNED`; the trip is the driver's own transitions with nothing to compensate, so
it is a row rather than a saga; `COMPLETED` opens the *settlement*. One petich engine runs both —
`supports(payload)` filters the interceptor list, so two step lists in one engine is the design
rather than a compromise.

**The offer is a suspended saga, not a step that waits.** `OfferStep` reserves a driver, posts the
offer and returns `Suspend`: the saga is parked in the database holding neither a thread nor a
connection, and the driver's answer resumes it. petich's own TTL is the whole matching budget — if
nobody answers anything for ninety seconds the sweeper rolls the saga back — while the fifteen-second
per-driver deadline and the cascade to the next candidate are this service's.

**`requireOutbox = true`, and the metric refuses.** An engine whose repository cannot store events
would otherwise drop them, complete, and leave only the consumer at the far end never running. The
engine refuses to be built over the wrong repository, and `onDroppedEvents` throws rather than
counting — see `OrderSaga.kt`.

**Two structures are in memory on purpose**: the geo-index of online drivers and the offer board.
Both are caches whose record is elsewhere (the saga's row, and the socket that reports positions), and
both are the reason this service runs as **one replica** — see [B-36](../backlog/B-36-a-chart-for-somewhere-else.md).

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Database | PostgreSQL | the sagas, the outbox, the trip and the payout ledger |
| Broker | booblik | `ride-events` — what happened to a ride. Positions never enter a topic (research §1.6a) |
| Service | shildik | tokens for the rider's own routes |
| Service | bochka | hosts the pmtiles archive the browser fetches; this server links nothing from it |
| Service | katcher | crash reports, sent by the browser and not by this server |
| External | an SMTP relay | receipts, sent by the settlement saga |
| Embedded | GraphHopper | roads and ETAs, in this process — a route is a method call |

## 5. Infrastructure and deploy

* **Image:** `shashki/server:<commit>`, built by `./gradlew :server:image -PosmFile=<city.osm.pbf>`.
  It carries the application, the prepared road graph and both browser bundles.
* **Chart:** none yet — [B-36](../backlog/B-36-a-chart-for-somewhere-else.md).
* **Health:** `GET /health`
* **Metrics:** none yet — [B-39](../backlog/B-39-the-service-can-be-watched.md).
* **The bundles are served from here**, the rider at `/` and the driver at `/driver`, so the demo is
  one artefact. The cost is stated rather than hidden: the two prefixes each fetch the 8.6 MB Compose
  runtime they share byte for byte, so D10's saving needs a deployment that puts the content-hashed
  runtime on one path.

## 6. Local setup

```bash
./gradlew :server:image -PosmFile=~/shashki-city/Ljubljana.osm.pbf
docker compose -f docker/compose.yaml up -d
bash docker/bootstrap-shildik.sh
bash docker/upload-tiles.sh ~/shashki-city/Ljubljana.pmtiles
open http://127.0.0.1:18080
```

Without an image, `./gradlew :server:run` against a Postgres works and has no map, no broker and no
provider — each absence is logged at `warn` with what it costs.

## 7. Configuration

Read where it is used, not from a central file: `DatabaseFactory.kt`, `AuthConfig.kt`,
`RoutingConfig.kt`, `ReceiptConfig.kt`, `EventsConfig.kt`, `BundleRouting.kt`.

| Key | Description | Required |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | the database | yes |
| `SHASHKI_OSM_FILE`, `SHASHKI_GRAPH_DIR` | the road graph; without either, distances are straight lines | no |
| `SHASHKI_OIDC_ISSUER`, `_REALM`, `_CLIENT` | the provider; without it the rider's routes are open | no |
| `SHASHKI_BOOBLIK` | the broker; without it the relay does not start and events stay in the outbox | no |
| `SHASHKI_SMTP_*` | the mail relay; without it receipts are recorded as unsent | no |
| `SHASHKI_BUNDLES`, `SHASHKI_TILES_URL`, `SHASHKI_KATCHER_*` | what the served page tells the bundles | no |

## 8. Quirks

* **Every optional dependency is absent by default and says so at `warn`.** That is deliberate — a
  demo has to start — and it means a misconfigured deployment looks exactly like a working one until
  somebody reads the log. The lines name the consequence rather than the missing file.
* **`riderId` and `driverId` are request fields, not token claims.** A token proves somebody signed
  in; that the ride is theirs is checked only where the *order saga* recorded the driver
  (`AdvanceTripUseCase`). B-09's remainder.
* **The payment gateway is in memory.** Restart and every hold is gone, which for a mock is a feature.
* **The ride history is a projection with no store**, rebuilt from the broker on start — so what
  retention has dropped is not in it, and no database would bring it back either.
