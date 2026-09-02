---
id: B-38
title: "Ride events reach booblik, so the broker stops being a comment"
status: open
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
