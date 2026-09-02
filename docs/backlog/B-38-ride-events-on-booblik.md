---
id: B-38
title: "Ride events reach booblik, so the broker stops being a comment"
status: done
priority: P1
size: M
stage: stage-2-saga
---

# B-38 — Ride events reach booblik, so the broker stops being a comment

`OutboxRelayWorker` runs and delivers what the outbox holds — to `LoggingPublisher`, a private object
in `Application.kt` whose comment reads "booblik is a later item". There is no later item. The
transactional outbox is the hard half and it is built and tested; what is missing is the four lines
at the end of it, and their absence means the stack's broker appears in this product only as the
word in that comment.

- **The outbox is the part that was worth building and it is already right.** A ride's state change
  and the record of it are written in one transaction, so nothing is published that did not happen
  and nothing that happened goes unpublished — that is B-11's property and it does not change here.
  This item is the delivery end.
- **`ride-events`, and one consumer, because a topic nobody reads proves nothing.** A publisher on
  its own is indistinguishable from `LoggingPublisher` for anybody looking at the outside. The
  consumer does not have to be interesting — reading the stream and counting what a ride went through
  is enough to show the seam works — but it has to exist and it has to be a separate concern from
  the saga that wrote the events.
- **§1.6a already settled what does *not* go through it**, and that boundary is the reason the broker
  is credible here: driver positions go straight into the geo-index over WebSocket and never enter a
  topic, because a position is worth a minute and a topic partitioned by ride would carry thousands a
  second to be read once. What goes through booblik is what happened to a **ride**.
- booblik is **JVM only** (§1.6, `booblik-client/build.gradle.kts`), which costs nothing: the broker
  was always on the server side of the line.
- The rejected alternative is publishing from the saga directly and deleting the outbox. It is
  simpler and it is exactly the failure the outbox exists to prevent — a commit that succeeds and a
  publish that does not.
- Deliberately **not** covered: a second service consuming the topic. The "microservice" story here
  is told by the saga and the broker, not by ten deployments — `server/build.gradle.kts` says so and
  it is still true.

- AC: a ride's lifecycle events are published to booblik by the relay, and killing the process
  between the commit and the publish loses nothing — the same test shape B-11 already uses, extended
  past the outbox row.
- AC: a consumer reads the topic and can say what a given ride went through, from the events alone.
- AC: `LoggingPublisher` is deleted rather than left as a fallback. A publisher that silently logs
  when the broker is unreachable is a broker outage nobody notices.
- AC: booblik's coordinate is a release or a CI-numbered publish, not `-SNAPSHOT` — B-13's rule, and
  this is the item that adds the dependency.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/Application.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/saga/OrderSaga.kt`

## What it turned out to be

**Four lines at the far end, and the four lines were the whole point.** The outbox was already right
— a ride's state change and the record of it in one transaction, retried with backoff, dead-lettered
after five attempts — and it delivered to a `LoggingPublisher` whose own comment said booblik was a
later item. There was no later item, so the stack's broker existed in this product as a word in a
comment.

**What replaced the log publisher is nothing.** With no `SHASHKI_BOOBLIK` the relay is *not started*:
the events stay in the outbox, unpublished and undelivered, and the server says so at `warn`. That
reads worse than a log line and is better — a publisher that writes a line and marks the event
delivered is not a fallback, it is a broker outage nobody can notice. The AC asked for
`LoggingPublisher` to be deleted rather than kept as a fallback; deleting it left the question of
what a server with no broker does, and the answer is: less than it did, honestly.

**The consumer is what makes the publisher distinguishable from what it replaced.**
`GET /api/rides/{id}/history` is served from a projection built *only* from records taken off the
topic — it never reads the saga's row, which every other route in this server does. A ride the topic
has nothing about answers with an empty list rather than 404, because "the broker has nothing about
it" and "there is no such ride" are different facts and the ride's own route already answers the
second.

**Keying by ride id is what makes a history a sequence.** booblik picks the partition from the key
client-side, so everything that happens to one ride lands on one partition in the order it happened
— and the test asserts the offsets increase rather than merely that both events arrived. The id is
read off the outbox record (`<rideId>:assigned`) rather than carried as a second field: if a future
event ever has a different shape of id the partition is wrong and nothing else is, which is the
failure this can afford.

**Koin decided the shape of the absence.** `single<BooblikOutboxPublisher?>` does not compile —
`get<T>` is bound to `T : Any` — so "no broker" is an `Events` wrapper holding two nulls, which is
the shape `CrashReporting` already has on the client. My first attempt argued against exactly that
wrapper in a comment; the type system settled it.

**And `verify()` had to be told.** `Events` is built by a lambda that constructs both halves from one
address, so the static verifier saw a two-parameter constructor and asked the container for each —
declared per definition rather than as a global `extraTypes`, so a genuine disappearance of the same
type elsewhere stays visible.

The end-to-end runs against a real broker and is gated on one: a ride is assigned and driven to
completion, and both events cross the database, the relay, booblik and a consumer that shares nothing
with the saga before coming back as a history somebody can read. Plus three ungated tests of the
projection, including the one rule it has — a batch that arrives twice is one event, because a
reconnecting consumer re-reads from where it was.
