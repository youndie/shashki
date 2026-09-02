---
id: B-45
title: "History: the rider's rides, drawn natively from the broker's projection"
status: open
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
