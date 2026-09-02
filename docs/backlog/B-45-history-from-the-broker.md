---
id: B-45
title: "History: the rider's rides, drawn natively from the broker's projection"
status: done
priority: P1
size: M
stage: stage-5-the-rest-of-the-kit
---

# B-45 — History: the rider's rides, drawn natively from the broker's projection

The kit's R9 is a pivot — *trips · profile · promo* — and only *promo* exists, because D11 gave the
server one screen and that was it. `GET /api/rides/{id}/history` (B-38) already answers what happened
to one ride from booblik's topic alone; there is no way to ask which rides are *mine*, and nothing
draws a `TripRow`, which B-17 built and B-32 found joined to nothing.

- **The list comes from the saga store, the detail from the projection, and the seam is the point.**
  `GET /api/rides?mine` is the saga's rows for the principal — that is where "which rides" lives.
  What each ride *went through* is the broker's projection, read per ride, so the history screen is
  the one place in the product where a reader can see the two stores disagree if they ever do.
- **Native, not server-driven, and D11 says why.** A list of the rider's own rides has an obvious
  native version; the argument for BDUI is a screen with none. `TripRow` and `FareBreakdown` were
  registered as kompot components in B-17 — they stay registered, and this screen draws them as plain
  composables. A component that can be both is the property, not a contradiction.
- **The pivot is the top level and the server may not nest it** — the kit's rule 5, which B-17 turned
  into a renderer invariant. Here it is simply `KvadrantPivot` with three items, of which the third is
  the existing `ServerScreen`.
- The rejected alternative is a history built from the saga rows alone. It is one query and it makes
  B-38's projection a thing only a test reads.
- Deliberately **not** covered: *profile*. Name and e-mail come off the token and there is nothing to
  edit; the pivot item shows them and stops.

- AC: `GET /api/rides?mine` under the rider token, newest first, and a rider sees only their own.
- AC: `rider_history` and `rider_history_empty` goldens against R9 and its empty state — the
  headline in the disabled brush, no action, the empty-list rule from the kit's section 08.
- AC: the fare on a `TripRow` is the settlement's captured amount, and a cancelled-after-`ASSIGNED`
  ride shows the fee, so the two cancellations are told apart in the list too.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/feature/events/EventsRouting.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ServerDrivenComponents.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/`

## What it turned out to be

**"Newest first" is where this stopped being a query.** petich's table is `id, type, phase, index,
status, payload, enriched, version, suspended_until` — there is no clock anywhere in it, and the
saga store is the only place that knows which rides exist. Adding a column would have put a fact
petich does not know about into a schema it owns, so the timestamp went into `OrderPayload`, where
the rest of the ride already lives; rows written before it carry `0` and sort last, which is what a
zero honestly means here.

**The list reads ids and asks petich for each row.** A query that parsed the `payload` column itself
would be a second implementation of how a saga is stored, drifting the first time petich changed it —
so `SagaIndex` answers with keys and the repository asks for rows, an N+1 that is honest about being
one. The day a rider has hundreds of rides the answer is a projection off the broker's topic, which
is the seam this screen exists to show.

**Whose a ride is turned out to be already answered.** B-26 put the rider's address on the saga from
the token so a receipt could not go to somebody else's inbox; that address is the only identity the
store has, and it is what `?mine` filters on. With no provider there is no address and no principal,
and every ride belongs to the demo's one rider — written down rather than answered with an empty
list nobody could explain.

**Two things the drawing found.** A `LazyColumn` inside the kit's pivot throws — the page is measured
with an unbounded height — which is the trap `client-feature-impl` names and which the golden caught
on the first record; a rider's history is a handful of rows and a plain `Column` is honest for it.
And the list renders through kompot's own `TripRowRenderer` rather than a native copy, so the
component B-17 built and B-32 found joined to nothing is now drawn twice from one implementation.

- AC 1: `GET /api/rides?mine=true` under the rider's tier, newest first, filtered by the token's
  address; `the rider's own rides come back newest first, with what each one cost`.
- AC 2: `screens_rider_history` and `screens_rider_history_empty` — the kit's section 08 empty state,
  one line in the disabled brush and no action. Light variants with
  [B-48](B-48-light-goldens-for-every-screen.md).
- AC 3: the amount on a row is `chargedCents`, so a finished ride shows the fare, a cancellation
  shows the fee and a ride nobody drove shows `—`. Asserted on both sides: the route test and
  `HistoryViewModelTest`.
- What is deliberately thin: *profile* shows the configured id and the token's `email` claim, read
  without verification — the server verifies, once, and this is a label on a screen. There is nothing
  to edit, which is the item's own limit.
