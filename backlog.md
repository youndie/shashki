# Backlog: shashki

> Role of this document: the product backlog. **One file per item in
> [`docs/backlog/`](docs/backlog/)** — `B-NN-<slug>.md`. What lives here is the index (generated) and
> everything that is not an item: the goal, the stages, and the decisions worth not re-litigating.
>
> New item: copy [`docs/templates/backlog-item.md`](docs/templates/backlog-item.md), take the next
> free `B-NN`, and run `python3 scripts/backlog_index.py` after editing.

## Goal

shashki has to look like a product and behave like a reference. Those pull in opposite directions:
a product hides its seams, and a reference exists to show them. The backlog is ordered by that
tension. First the three unknowns that decide the shape of everything else — where the map can run,
where the goldens can run, and what the design system actually is once the kit is checked against
the library. Then the skeleton. Then the order saga, which is the one part of the product a stranger
can judge without knowing anything about the stack: the driver was assigned, the process died, and
the card was not left holding money. Everything that makes the demo pleasant comes after that,
because a pleasant demo of a broken saga is worse than no demo.

Every item cites [`docs/research/research-architecture.md`](docs/research/research-architecture.md)
by section. Research is where the reasons live; the item is the work.

## Stages

A stage is a field on the item, not a directory. Items are cited by id from every layer, so
re-prioritising one must never move its file.

| Stage id | Stage | What it is |
|---|---|---|
| `stage-0-unknowns` | Remove the unknowns | The questions whose answers change what gets built: the browser route, the golden host, the real shape of the design system. Nothing downstream is worth designing until these land. |
| `stage-1-skeleton` | Something builds and can be looked at | Modules, targets, versions, sign-in, tiles served. The point at which a screen can be put on top of something. |
| `stage-2-saga` | The order survives the process dying | The core of the demo: phases, compensations, the outbox, and an offer that waits for a human without holding anything. |
| `stage-3-surface` | Everything past the core | Receipts, crash reports, rebuildability — the parts that are ordinary work once the three above are settled. |

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (10)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-13](docs/backlog/B-13-pin-every-dependency.md) `[ ]` | Every dependency is a release or a pinned snapshot before the demo is published | P1 | S | - |
| [B-23](docs/backlog/B-23-routes-and-eta-on-embedded-graphhopper.md) `[ ]` | Routes and ETA through GraphHopper embedded in the server | P1 | M | - |
| [B-24](docs/backlog/B-24-motorways-carry-ref-not-name.md) `[ ]` | Motorways carry ref and not name, so the styles label none of them | P1 | XS | - |
| [B-25](docs/backlog/B-25-rider-trip-in-progress.md) `[ ]` | The rider's trip-in-progress screen, on the map that D1 chose | P1 | M | - |
| [B-26](docs/backlog/B-26-sign-in-end-to-end.md) `[ ]` | Rider and driver actually sign in, against a running shildik | P1 | M | B-09 |
| [B-07](docs/backlog/B-07-serve-pmtiles-from-bochka.md) `[ ]` | Serve the pmtiles archive out of bochka and measure ranged reads | P2 | S | - |
| [B-10](docs/backlog/B-10-crash-reports-from-the-browser.md) `[ ]` | Crash reports from the browser go over katcher's ingest endpoint | P2 | S | - |
| [B-14](docs/backlog/B-14-receipt-over-smtpkn-jvm.md) `[ ]` | The e-mail receipt runs on smtpkn's JVM target, gated by a test against Mailpit | P2 | M | - |
| [B-16](docs/backlog/B-16-one-bundle-or-two.md) `[?]` | One wasm bundle or two | P2 | XS | B-01 |
| [B-17](docs/backlog/B-17-kompot-renderer-invariants.md) `[ ]` | The kit's composition rules live in the kompot renderer, not in the protocol | P2 | M | B-03 |

## Closed (16)

**Remove the unknowns**

- [B-01](docs/backlog/B-01-decide-the-browser-route.md) `[x]` - Decide how the clients reach a browser, and write the choice down
- [B-02](docs/backlog/B-02-measure-golden-host-independence.md) `[x]` - Measure whether shashki's goldens are host-independent
- [B-03](docs/backlog/B-03-shashki-foundation-module.md) `[x]` - The foundation values: shashki's ramp, spacing, ink and golden pin
- [B-04](docs/backlog/B-04-classtile-and-offercard.md) `[x]` - ClassTile and OfferCard on kvadrant primitives
- [B-05](docs/backlog/B-05-glyph-coverage-guard.md) `[x]` - Every fixture string is checked for glyph coverage
- [B-06](docs/backlog/B-06-city-extract-and-tiles.md) `[x]` - Produce the OSM extract and the pmtiles archive for Ljubljana
- [B-15](docs/backlog/B-15-answer-the-kits-open-questions.md) `[x]` - Settle the kit's 4/3 spacing: as drawn, as converted, or fitted
- [B-18](docs/backlog/B-18-kvadrant-overridable-on-accent.md) `[x]` - kvadrant-ui: onAccent becomes overridable, keeping the computed value as the default
- [B-19](docs/backlog/B-19-kvadrant-app-bar-tokens.md) `[x]` - kvadrant-ui: the app bar's dimensions become theme tokens
- [B-21](docs/backlog/B-21-ramp-projection-against-stock-components.md) `[x]` - The ramp projection is checked against the stock components that read it, by golden
- [B-22](docs/backlog/B-22-publish-kvadrant-ui-with-the-hooks.md) `[x]` - kvadrant-ui: publish 0.2.0 with the two hooks, so B-03 stops waiting on a merge

**Something builds and can be looked at**

- [B-08](docs/backlog/B-08-repository-skeleton.md) `[x]` - The repository skeleton: modules, targets, versions and the check target
- [B-09](docs/backlog/B-09-browser-side-pkce.md) `[x]` - Authorization code with PKCE from the browser is shashki's code

**The order survives the process dying**

- [B-11](docs/backlog/B-11-order-saga-on-petich.md) `[x]` - The order saga on petich, with the outbox required rather than optional
- [B-12](docs/backlog/B-12-offer-as-a-suspended-saga.md) `[x]` - The driver offer is a suspended saga with a deadline, not a step that waits
- [B-20](docs/backlog/B-20-matching-geo-index-and-driver-simulator.md) `[x]` - Matching: the geo-index, the candidate query and the driver simulator

<!-- END INDEX -->

## Decisions worth not re-litigating

**The brief's first step was right and its question was wrong.** The brief opens with a spike,
"MapLibre Compose in wasm", to retire the main risk. Research §1.3 answered that spike from published
metadata: the library has no `wasmJs` variant, and the upstream work is pinned to an unreleased
Compose. [B-01](docs/backlog/B-01-decide-the-browser-route.md) keeps the spike first and changes what
it asks. This is worth keeping written down, because "just try MapLibre in wasm" is the obvious first
suggestion and will be made again.

**The fourth route is not a fallback.** Drawing the map ourselves in Compose was the brief's
"optional v2 demo"; it is now one of four candidates, because §1.8 measured it rather than imagined
it — 13 layers, seven paint properties, seven expression operators, no sprites, no icons, no halos,
and a curved street label reachable through skiko's `PathMeasure.getRSXform` on the wasm target.
Routes 1–3 buy a map and leave four items open; route 4 closes all four and opens exactly one, which
is how much tile pipeline this project is willing to own. Every other self-hosted piece of this stack
answered a version of that question the same way, and that is a precedent rather than a measurement.

**kvadrant-ui is the base and it is pulled towards the kit — its vocabulary, not its answers.** The
kit calls its whole foundation inherited. Research §1.1 verified the colour half hex for hex and
found the other half is a new set: four of seven type pairings are new weights, and every spacing
number is exactly 4/3 of the library's. The research first concluded that escalation was closed and
shashki should fork the foundation; the owner of both repositories decided otherwise, and §1.1f then
split the work in two. Type and spacing are already expressible — both are `data class`es the theme
takes as arguments — so they are values in shashki
([B-03](docs/backlog/B-03-shashki-foundation-module.md)). The ink and the app bar are not expressible
at all, and those are gaps closed upstream
([B-18](docs/backlog/B-18-kvadrant-overridable-on-accent.md),
[B-19](docs/backlog/B-19-kvadrant-app-bar-tokens.md)).

What was **not** decided, and is worth keeping distinct: moving kvadrant-ui's *defaults* to the kit's
values. Every metric there carries the Metro pixel count it was converted from, so changing a default
falsifies a claim about a verified source for every consumer in order to suit one. Growing the
library's vocabulary takes nothing away; changing its answers takes away the reason to trust the
rest of them.

**One ride is two sagas with a trip between them, and the word "cancel" means a different thing on
each side of `ASSIGNED`.** The order saga runs the five phases once, `REQUESTED → ASSIGNED`; the trip
is the driver's transitions and location, with nothing to compensate; `COMPLETED` opens the
settlement saga. Research §1.4c records it because the first `RideStatus` KDoc said the enum *was*
the saga, and a matching design built on that reading would have put the trip's states inside
EXECUTION. Matching itself, and the simulated drivers a cascade needs, were not items at all until
[B-20](docs/backlog/B-20-matching-geo-index-and-driver-simulator.md).

**A missing target is a finding, not a blocker.** Research §1.6 found four libraries whose published
targets do not match what the brief assumes — booblik's client is JVM-only, katcher's has no browser
variant, shildik's client has none either, smtpkn's JVM target is unclaimed. Three of the four cost
almost nothing once seen: the broker is server-side anyway, katcher has a documented HTTP ingest, and
the browser half of PKCE is small. They are listed separately rather than as one "target audit" item
because each is a different decision.
