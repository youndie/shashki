---
id: B-11
title: "The order saga on petich, with the outbox required rather than optional"
status: done
priority: P0
size: L
stage: stage-2-saga
---

# B-11 — The order saga on petich, with the outbox required rather than optional

Research §1.4 confirmed the brief's phase mapping is exact: `ENRICHMENT → VALIDATION →
AUTHORIZATION → EXECUTION → POST_PROCESSING` is what `PetichPhase` declares. It also found that the
outbox is opt-in — without `requireOutbox = true` an engine whose repository cannot store events
drops them, the saga completes, its state is correct, and nothing anywhere says an event was lost.

- **Turn it on at construction, and wire `onDroppedEvents` anyway.** The brief's whole
  post-processing story is the outbox publishing `ride-events` into booblik; a silent drop is the one
  failure this demo exists to make impossible.
- The rejected alternative is the default. It is the right default for an application with no broker
  and the wrong one for this.
- Covers the compensations too: rider cancellation is compensation from the middle of the saga, which
  is the same mechanism read backwards and the demo's point.

- AC: killing the process at each phase boundary leaves no held payment and no reserved driver.
- AC: `PetichEngineConfig(requireOutbox = true)`, and a test that a repository without outbox support
  fails to build an engine.
- Anchors: `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `petich/petich-postgres`

## What it turned out to be

One feature package, `server/.../feature/ride`, laid out by `server-feature-impl`: wire types and the
`@Resource` routes in `:protocol`, domain (`RideRepository`, `RequestRideUseCase`) and data
(`PetichRideRepository`) in the server, a Koin module, `POST /api/rides` and `GET /api/rides/{id}`.
Five steps, one per phase, each in the package of the boundary it belongs to: `QuoteStep` (pricing),
`ServiceAreaStep`, `HoldPaymentStep` (billing), `ReserveDriverStep` (dispatch), `PublishAssignedStep`.
Postgres through petich-postgres over Exposed 1.4.0 — petich's own pin, not a choice — with Flyway's
`V1__petich.sql` hand-written and a `SchemaTest` that asks Exposed whether it agrees.

- ~~AC: killing the process at each phase boundary leaves no held payment and no reserved
  driver.~~ Done, and the test is literal: for every boundary before POST_PROCESSING, the next step
  throws — which is what an unplugged process looks like to the saga — and afterwards the gateway
  holds nothing, the reservations are empty and the outbox is empty.
- ~~AC: `PetichEngineConfig(requireOutbox = true)`, and a test that a repository without outbox
  support fails to build an engine.~~ Done — `RequireOutboxTest`, with the counter-case that the same
  repository is *accepted* when the flag is off, because that acceptance is the defect being guarded.
  `onDroppedEvents` throws rather than counts, for the same reason.

**A saga the process abandoned is finished by the next process, and the test had to be rewritten
to say so honestly.** The first version parked the saga with a step that returned `Suspend` and
handed it to a fresh engine, which answered `SystemFailure` — correctly: a suspended row is
`PENDING_SIGNATURE` waiting for a resume payload, not a dead process. What `kill -9` after
AUTHORIZATION actually leaves is a `PROCESSING` row at the start of EXECUTION with the hold id in
its enriched payload, and a real hold in the gateway. The test now builds exactly that row by hand
and shows a new engine continuing from EXECUTION — one hold, not two — which is petich's own
`process()` reading the persisted phase and index. Research §1.4 records the distinction.

**The skill's named trap bit on the first request.** `factoryOf(::RequestRideUseCase)` resolved the
use case's `ids: () -> String` default through Koin and answered 500 with
`NoDefinitionFoundException` for `Function0`; compilation was silent. An explicit `factory { }`
lambda, and the comment beside it says why.

**Three stand-ins, each bound to a port, each named for the item that replaces it.** Straight-line
routes at 30 km/h (B-23), an in-memory payment gateway with holds (the brief's mock cashier, real
contract), a fixed list of three candidates (B-20). The saga does not know which it has. Every
number in `Pricing` and the service-area box is a hypothesis and says so in KDoc.

**Auth tier: public, temporarily, and chosen rather than inherited.** `riderId` arrives in the
body until B-09 puts it in a token; the routing's KDoc records the tier and what changes it.

**What the ride's status means, in one function.** A *completed* order saga is an `ASSIGNED` ride —
the trip has not started — and a rejected, failed or compensating one is `CANCELLED`
(`PetichRideRepository.rideStatus`). That mapping is research §1.4c made executable.

**Not here, by design:** the suspended offer and its cascade (B-12), candidates and the simulator
(B-20), real routes (B-23), the broker — `OutboxRelayWorker` publishes to the log, which proves an
event *leaves* the outbox and nothing about where it goes.

**Amended the same evening: the Koin graph is tested, and the test's blind spot is measured.**
`KoinGraphTest` verifies `rideModule` statically with `koin-test`'s `verify()` and then resolves a
binding by type from a built `koinApplication`. The two halves are both there because one is not
enough, and that was measured rather than assumed: `verify()` reflects over the bound type's
constructor for every definition — `single { … }` included, which is why `PetichEngine`'s six
hand-supplied arguments are declared through `injections` — but it **skips any parameter with a
default**, `() -> String` and `Int = 5` alike, while it does report a non-defaulted lambda as a
missing `Function0`. So the trap that answered 500 on the first request passes `verify()` and fails
only on resolution, wrapped in `InstanceCreationException`. Three controls in the file hold each of
those statements, and the first draft of one of them sabotaged itself with `error()` stubs — Koin
resolves constructor parameters in order, so the stub threw before the parameter being measured
was reached. Both findings went into the `server-feature-impl` skill.
