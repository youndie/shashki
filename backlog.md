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
| `stage-4-elsewhere` | It runs somewhere that is not this laptop | Packaging and deployment. A separate stage because the question changes: the four above ask whether it works, this one asks whether somebody else can run it — and the answers are artefacts rather than code. |
| `stage-5-the-rest-of-the-kit` | The rest of the kit, and the hole the endpoint table names | v2. v1 closed with every library placed and about seven of the kit's twenty-five screens drawn (research §5). What is left is the product's surface — the wait, the cancel, the rating, the history, the earnings — and one documented hole, the driver's public routes, which goes first because it is the one item that makes a written guarantee false. |
| `stage-6-what-running-it-said` | What running it said | v1 and v2 both closed with a green `check` over a product that could not be signed into and a driver that could not go online. This stage is what one evening of opening the thing produced: eleven items, none of which a test had asked about, and the pattern is the repository's own — a mechanism written at both ends and joined at neither, plus a guard that covered the cases somebody remembered. |

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (1)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-85](docs/backlog/B-85-the-public-surface-only-its-own-tests-reach.md) `[ ]` | Twenty-four public declarations that nothing but their own tests reaches | P2 | M | - |

## Closed (89)

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

- [B-07](docs/backlog/B-07-serve-pmtiles-from-bochka.md) `[x]` - Serve the pmtiles archive out of bochka and measure ranged reads
- [B-08](docs/backlog/B-08-repository-skeleton.md) `[x]` - The repository skeleton: modules, targets, versions and the check target
- [B-09](docs/backlog/B-09-browser-side-pkce.md) `[x]` - Authorization code with PKCE from the browser is shashki's code
- [B-16](docs/backlog/B-16-one-bundle-or-two.md) `[x]` - One wasm bundle or two
- [B-24](docs/backlog/B-24-motorways-carry-ref-not-name.md) `[x]` - Motorways carry ref and not name, so the styles label none of them
- [B-26](docs/backlog/B-26-sign-in-end-to-end.md) `[x]` - Rider and driver actually sign in, against a running shildik
- [B-27](docs/backlog/B-27-deprecations-only-a-clean-build-shows.md) `[x]` - The deprecations only a clean configuration shows

**The order survives the process dying**

- [B-11](docs/backlog/B-11-order-saga-on-petich.md) `[x]` - The order saga on petich, with the outbox required rather than optional
- [B-12](docs/backlog/B-12-offer-as-a-suspended-saga.md) `[x]` - The driver offer is a suspended saga with a deadline, not a step that waits
- [B-20](docs/backlog/B-20-matching-geo-index-and-driver-simulator.md) `[x]` - Matching: the geo-index, the candidate query and the driver simulator
- [B-23](docs/backlog/B-23-routes-and-eta-on-embedded-graphhopper.md) `[x]` - Routes and ETA through GraphHopper embedded in the server
- [B-37](docs/backlog/B-37-the-settlement-saga.md) `[x]` - The settlement saga, whose parts are all written and none of them called
- [B-38](docs/backlog/B-38-ride-events-on-booblik.md) `[x]` - Ride events reach booblik, so the broker stops being a comment
- [B-42](docs/backlog/B-42-a-driver-is-reserved-for-life.md) `[x]` - A driver who finishes a ride is reserved for ever, and the rider is still shown their wait

**Everything past the core**

- [B-10](docs/backlog/B-10-crash-reports-from-the-browser.md) `[x]` - Crash reports from the browser go over katcher's ingest endpoint
- [B-13](docs/backlog/B-13-pin-every-dependency.md) `[x]` - Every dependency is a release or a pinned snapshot before the demo is published
- [B-14](docs/backlog/B-14-receipt-over-smtpkn-jvm.md) `[x]` - The e-mail receipt runs on smtpkn's JVM target, gated by a test against Mailpit
- [B-17](docs/backlog/B-17-kompot-renderer-invariants.md) `[x]` - The kit's composition rules live in the kompot renderer, not in the protocol
- [B-25](docs/backlog/B-25-rider-trip-in-progress.md) `[x]` - The rider's trip-in-progress screen, on the map that D1 chose
- [B-28](docs/backlog/B-28-the-client-application-shell.md) `[x]` - A client application exists to put the screens in
- [B-29](docs/backlog/B-29-the-driver-bundle.md) `[x]` - The driver bundle, which is the second one D10 chose
- [B-30](docs/backlog/B-30-tiles-over-the-wire.md) `[x]` - The map fetches its tiles, so the streets are there
- [B-31](docs/backlog/B-31-the-wait-for-a-car.md) `[x]` - The wait for a car, which the kit puts on every class tile
- [B-32](docs/backlog/B-32-which-screens-the-server-sends.md) `[x]` - Which screens the server sends, and which the client draws
- [B-33](docs/backlog/B-33-take-the-upstream-fixes.md) `[x]` - The three upstream fixes landed; take them and delete what they replace
- [B-34](docs/backlog/B-34-a-browser-on-the-build-box.md) `[x]` - A headless browser on the build box, so the wasm target is run and not only compiled
- [B-40](docs/backlog/B-40-the-documentation-layers.md) `[x]` - The layers below the research, which the rule about main has been holding empty
- [B-41](docs/backlog/B-41-the-rider-actually-signs-in.md) `[x]` - The rider application actually signs in, and puts the token on its requests

**It runs somewhere that is not this laptop**

- [B-35](docs/backlog/B-35-the-server-as-an-image.md) `[x]` - The server as an image, and the graph that has to be inside it
- [B-36](docs/backlog/B-36-a-chart-for-somewhere-else.md) `[x]` - A chart, and the honest replica count that goes in it
- [B-39](docs/backlog/B-39-the-service-can-be-watched.md) `[x]` - The service can be watched: metrik, tracy, telek and kompot's degradation sink
- [B-50](docs/backlog/B-50-a-smaller-image.md) `[x]` - A smaller image: the 569 MB that B-35 measured and did not touch
- [B-51](docs/backlog/B-51-the-page-on-kotlin-website.md) `[x]` - The page on kotlin.website, beside mani: what shashki shows, in the stack's own words

**The rest of the kit, and the hole the endpoint table names**

- [B-43](docs/backlog/B-43-the-rider-sees-the-wait-and-its-end.md) `[x]` - The rider sees the wait and its end: matching, no cars nearby, and cancel
- [B-44](docs/backlog/B-44-finished-rate-and-tip.md) `[x]` - Finished: rate the driver and tip, and the tip is a second charge
- [B-45](docs/backlog/B-45-history-from-the-broker.md) `[x]` - History: the rider's rides, drawn natively from the broker's projection
- [B-46](docs/backlog/B-46-driver-earnings-from-payouts.md) `[x]` - Driver earnings: today, this week, and the payouts that already exist
- [B-47](docs/backlog/B-47-driver-onboarding-and-the-object-store.md) `[x]` - Driver onboarding, which is the one scenario the object store has left
- [B-48](docs/backlog/B-48-light-goldens-for-every-screen.md) `[x]` - Every screen fixture gains its light variant, which open question 1 promised
- [B-49](docs/backlog/B-49-the-drivers-real-position.md) `[x]` - The driver's real position, from the browser, or the reason it stays configured
- [B-52](docs/backlog/B-52-driver-routes-behind-the-token.md) `[x]` - The driver's four routes stop being public: the hole the endpoint table names

**What running it said**

- [B-53](docs/backlog/B-53-the-driver-bundle-cannot-go-online.md) `[x]` - The driver bundle sends an id the token contradicts, so every position frame is dropped
- [B-54](docs/backlog/B-54-the-shift-counter-counts-frames-nobody-took.md) `[x]` - The shift's count rises for frames the server threw away
- [B-55](docs/backlog/B-55-browser-sign-in-needs-an-unreleased-shildik.md) `[x]` - Browser sign-in cannot finish: the provider's CORS headers are unreleased
- [B-56](docs/backlog/B-56-an-uncaught-failure-is-a-blank-page.md) `[x]` - An uncaught failure leaves a blank page and no words at all
- [B-57](docs/backlog/B-57-one-condition-two-statuses.md) `[x]` - A pickup outside the graph is 422 on two routes and 500 on the one a rider uses
- [B-58](docs/backlog/B-58-the-rejection-nobody-writes.md) `[x]` - cancellationReason is on the wire, read by the repository, and written by nobody
- [B-59](docs/backlog/B-59-the-finished-screen-writes-and-never-reads.md) `[x]` - R8 asks for a rating it already has, and puts its one accent on skip
- [B-60](docs/backlog/B-60-d1-says-in-words-what-the-kit-says-in-glyphs.md) `[x]` - D1 states a document's status in words where the kit states it in a glyph
- [B-61](docs/backlog/B-61-the-history-row-and-the-receipt.md) `[x]` - R9's rows carry one address and no date, and R9·b does not exist
- [B-62](docs/backlog/B-62-a-price-for-a-class-you-cannot-order.md) `[x]` - R4 prices a class it has just said has no cars
- [B-63](docs/backlog/B-63-nobody-has-a-name.md) `[x]` - The product has no driver record, so a rider is asked to rate an e-mail address
- [B-64](docs/backlog/B-64-the-offer-reaches-the-client-and-not-the-screen.md) `[x]` - The offer reaches the driver's client and never reaches the driver's screen
- [B-65](docs/backlog/B-65-a-server-cannot-build-a-fare-breakdown.md) `[x]` - A server cannot build a FareBreakdown: the components live where Compose does
- [B-66](docs/backlog/B-66-the-class-picker-asks-once.md) `[x]` - R4 asks the server for a quote once and never again
- [B-67](docs/backlog/B-67-no-way-back-in-the-window.md) `[x]` - The desktop build can enter a screen and not leave it
- [B-68](docs/backlog/B-68-a-state-drawn-as-an-identifier.md) `[x]` - D4 draws in_progress where every other state is a word
- [B-69](docs/backlog/B-69-every-screen-of-one-type-shares-one-view-model.md) `[x]` - Every screen of one type shares one view model, so the second ride is shown the first
- [B-70](docs/backlog/B-70-the-driver-has-no-trip-summary.md) `[x]` - The driver finishes a trip and is shown nothing: D5 does not exist
- [B-71](docs/backlog/B-71-the-order-bar-answers-only-its-glyph.md) `[x]` - The order bar's label does nothing: only the 48 dp circle takes the tap
- [B-72](docs/backlog/B-72-a-class-tile-says-zero-minutes-and-no-car.md) `[x]` - A class tile says 0 min and names no car
- [B-73](docs/backlog/B-73-matching-says-less-than-the-kit-does.md) `[x]` - R5 says less than the kit's matching screen: no count, no clock, no class and price
- [B-74](docs/backlog/B-74-the-offer-card-is-white-where-the-kit-is-amber.md) `[x]` - D3's fare is white where the kit's is amber, and two of its lines are empty
- [B-75](docs/backlog/B-75-the-drivers-trip-screen-has-no-map-and-no-eta.md) `[x]` - D4 has no map and no time to the pickup
- [B-76](docs/backlog/B-76-the-plate-is-accent-where-the-kit-inverts-it.md) `[x]` - R6's plate is an accent chip; the kit inverts it — and the card says the trip's length, not the driver's
- [B-77](docs/backlog/B-77-in-progress-does-not-say-when-or-how-far.md) `[x]` - R7 does not say when you arrive or how far is left, and the travelled road stays lit
- [B-78](docs/backlog/B-78-history-rows-wrap-and-the-pivot-clips.md) `[x]` - R9's rows wrap unevenly, a cancelled ride says — for 0, and the pivot header is clipped
- [B-79](docs/backlog/B-79-the-receipt-names-a-uuid-and-not-a-journey.md) `[x]` - R9·b names a UUID and not a journey: no date, no addresses, no driver
- [B-80](docs/backlog/B-80-the-state-branches-the-kit-draws-and-the-product-cannot-reach.md) `[x]` - Four state branches the kit draws and the product cannot reach
- [B-81](docs/backlog/B-81-the-shift-screen-and-earnings-say-less-than-the-kit.md) `[x]` - D2 is a word and a button, and D6 is three sums: what the kit's tiles say that these do not
- [B-82](docs/backlog/B-82-pressing-a-tile-moves-nothing.md) `[x]` - Pressing a tile moves nothing: the kit's tilt was drawing inside the surface
- [B-83](docs/backlog/B-83-the-accept-bar-overran-the-decline-ring.md) `[x]` - The offer's accept bar is 293 dp where the kit caps it at 200, and overruns the decline ring
- [B-84](docs/backlog/B-84-the-first-ci-run-had-no-browser-and-said-no-tests.md) `[x]` - Every wasm suite on the first public CI run reported no tests, and neither half of the reason was the tests
- [B-86](docs/backlog/B-86-the-waiting-counters-label-reads-as-a-taxi-rank-not-a-heartbeat.md) `[x]` - The waiting screen's own count reads as a taxi-rank position, not the socket heartbeat it is
- [B-87](docs/backlog/B-87-the-recorded-reproduction-leaves-its-own-control-skipped.md) `[x]` - B-14's recorded reproduction sets three variables and its control needs four, so following it runs the half that proves nothing
- [B-88](docs/backlog/B-88-the-guards-that-need-the-stand-never-ran.md) `[x]` - Twelve guards need a stand, the stand runs for hours, and nothing ever pointed one at the other
- [B-89](docs/backlog/B-89-the-forgery-that-forged-nothing-one-run-in-four.md) `[x]` - The signature the acceptance test forges is unchanged one run in four, and the test then accuses the server
- [B-90](docs/backlog/B-90-a-moving-coordinate-in-a-published-build.md) `[x]` - Publishing the repository made a moving snapshot coordinate a defect, and pinning its root pinned half of it

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

**v1 is a boundary, not a finish line, and the boundary is written down.** Forty-one items closed
on 2026-09-02 with an empty backlog, and an empty backlog reads as "everything is done" rather than
"what was decided is done". Research §5 records the difference: every library in the brief is used,
hosted or explicitly dropped, both sagas run and are killed at their boundaries, and about seven of
the kit's twenty-five artboards are screens. The v2 stage exists so that the other eighteen are a
list rather than an impression — and so that the one documented hole, the driver's public routes,
does not survive as a sentence in an endpoint table. The first thing the stand found once v1 was
"done" — [B-42](docs/backlog/B-42-a-driver-is-reserved-for-life.md), a driver reserved for life after
one ride — is the same finding as B-32, B-37 and B-41 in a fourth place: two ends of one fact, joined
nowhere. That shape is now the thing to grep for before calling anything done.

**A missing target is a finding, not a blocker.** Research §1.6 found four libraries whose published
targets do not match what the brief assumes — booblik's client is JVM-only, katcher's has no browser
variant, shildik's client has none either, smtpkn's JVM target is unclaimed. Three of the four cost
almost nothing once seen: the broker is server-side anyway, katcher has a documented HTTP ingest, and
the browser half of PKCE is small. They are listed separately rather than as one "target audit" item
because each is a different decision.
