# docs — shashki

shashki is a reference ride-hailing service — rider client, driver client, dispatch server — built to
show the kotlin.website stack working together on a domain anybody can judge. The documentation is
layered; links run top to bottom.

```
[ Research (why the architecture is what it is) ]
                     │
[ Feature (business + BDD) ] ──▶ [ Client screen / flow ]
                                        │
                                        ▼
                              [ API endpoint (contract, auth tier) ]
                                        │
                                        ▼
                              [ Service (ownership, deploy) ]
```

| Layer | Directory | Answers | Source of truth |
|---|---|---|---|
| Research | `research/` | *why* it is built this way; what is verified, what is a hypothesis | the artefacts each fact names |
| Feature | `features/` | *what* the system does and *why*; BDD scenarios | this repository |
| Client | `screens/` | what the user sees: states, actions, navigation | this repository + the screen's code |
| API | `api/` | URL, method, auth tier, where the contract lives | the shared modules |
| Service | `services/` | who owns the data, dependencies, deploy, local setup | this repository |

**All five layers exist since 2026-09-02** ([B-40](../backlog/B-40-the-documentation-layers.md)).
For the first thirty-four items only `research/` did, and the rule that kept the others empty was
right: `main` describes what exists, and a document written before the code describes intent as fact.
The condition it was waiting for has now been met several times over — the screens are photographed,
the endpoints are tested, the service has two sagas in it — so what the rule protected had become the
absence itself.

**What the layers are for, and what they must not become.** The research records *why the shape is
this shape*, dated, with wrong ideas kept beside their corrections; a feature document says what the
system does today. Folding the second into the first is how a research document becomes a manual
nobody trusts, because a reader cannot tell which sentences are history. So these documents **link**
to a research section rather than restating it — a second copy of an argument is an argument that
will drift.

**Backlog** — [backlog.md](../backlog.md): the index and the decisions; the items themselves are one
file each in [`backlog/`](backlog/), cited as `[B-01](backlog/B-01-decide-the-browser-route.md)`.

## Conventions

- **`id`** in the frontmatter is unique and equals the filename.
- Cross-layer links are ids in the frontmatter and ordinary markdown links in the body.
- One document, one entity. A feature spanning three modules is **one** file with three entries in
  `involved_services`.
- BDD scenarios are written from the code, not from memory: check the actual status codes and error
  strings before writing a scenario. While the code does not exist, a scenario is marked *target*.
- **The primary consumer is a coding agent.** Every document carries code anchors — paths to the
  module, the handler, the view model — so the reader reaches the code in one hop. Do not duplicate
  what lives in code (DTO fields, config keys); give the path. A copy rots, a path does not.
- Language: **English**, throughout this tree and throughout the code. The design kit and the product
  brief this was derived from are Russian and stay Russian — they are evidence, and evidence is not
  translated.

## Templates

`templates/` holds a copy of the document templates, so the format travels with the repository.

## Checks

```bash
pip install pyyaml
make check
```

which is what CI runs, and is these five:

```bash
python3 scripts/backlog_index.py --check
python3 scripts/docs_check.py
python3 scripts/coverage_map.py --check
python3 scripts/bdd_report.py
python3 scripts/code_anchors.py --repos ..
```

The last two are reports rather than gates. `code_anchors.py` is worth reading here in particular:
while there is no shashki source tree, every anchor in the research points into one of the stack's
own repositories, and `--repos ..` resolves them only against whatever is checked out beside this
one. A repository that is not there is reported as missing anchors, which is the truth and not a
defect in the document — CI clones the whole list explicitly for exactly that reason
(`.github/workflows/check.yaml`, the `anchors` job).

## Coverage map

The list below is **checked** against the files on disk: a document missing here, or an entry with no
file behind it, fails `coverage_map.py`. The grouping and the descriptions are written by a person —
the machine only guards the membership.

### Research (1)

- [x] [research-architecture](research/research-architecture.md) — what was verified against the
  stack's own artefacts and against upstream metadata: the design system's real divergence from the
  kit, what the four routes to a browser actually cost (including drawing the map ourselves, priced
  in §1.8 rather than imagined), the twelve decisions taken, and six risks with the machinery that
  mitigates each

### Features (7)

The product, in the order a ride goes through it, then the two capabilities that are not a step in it.

- [x] [feature-order-a-ride](features/feature-order-a-ride.md) — the order saga: one road priced three
  ways, a hold, and drivers asked one at a time. The feature the product exists to demonstrate, and
  what is being demonstrated is what happens when the process dies part-way
- [x] [feature-the-trip](features/feature-the-trip.md) — the stretch between accepting and settling
  that is deliberately *not* a saga: four states, the driver's own, nothing to compensate
- [x] [feature-settlement](features/feature-settlement.md) — the second saga: capture, payout,
  receipt, events — and the same five phases with one different number when a rider cancels late
- [x] [feature-sign-in](features/feature-sign-in.md) — PKCE from the browser against shildik, and the
  token this server accepts. Neither half proves it alone
- [x] [feature-server-driven-promo](features/feature-server-driven-promo.md) — the one screen the
  server owns, and the boundary that keeps it the least important one
- [x] [feature-the-map](features/feature-the-map.md) — the basemap drawn by this product rather than
  by a library, because a map on somebody else's surface cannot appear in a golden
- [x] [feature-crash-reports](features/feature-crash-reports.md) — what a browser loses, and the two
  hooks that catch it

### Screens / Flows (7)

Five in the rider bundle, two in the driver's. Every one of them is photographed: the `Content` half
takes a state and a callback, so a golden of it needs no graph and no server.

- [x] [screen-rider-class-picker](screens/screen-rider-class-picker.md) — the map, where you are
  going, what each class costs and how long the wait is
- [x] [screen-rider-matching](screens/screen-rider-matching.md) — the wait, its unhappy end, and the
  confirmation that says what cancelling costs
- [x] [screen-rider-trip](screens/screen-rider-trip.md) — the road in two phases, the car on it, and
  the driver row whose registration is deliberately blank
- [x] [screen-rider-finished](screens/screen-rider-finished.md) — what the ride cost, the stars, and
  a tip that is a charge of its own
- [x] [screen-rider-promo](screens/screen-rider-promo.md) — whatever the server sent; the client owns
  the vocabulary and one action
- [x] [screen-driver-shift](screens/screen-driver-shift.md) — offline, waiting, or fifteen seconds to
  decide. One screen, because that is how a shift feels
- [x] [screen-driver-assigned-ride](screens/screen-driver-assigned-ride.md) — what was accepted, and
  the one button that moves it along

### API (4)

Grouped by feature rather than by path, so the auth tier of a route is read beside the reason for it.

- [x] [endpoint-rides](api/endpoint-rides.md) — the rider's own surface, including the one word that
  means two mechanisms: cancel
- [x] [endpoint-quotes](api/endpoint-quotes.md) — what a journey costs before anybody orders it, and
  why both routes are public
- [x] [endpoint-driver](api/endpoint-driver.md) — the position socket, the offer board and the trip's
  transitions, with the auth hole named rather than implied
- [x] [endpoint-screens](api/endpoint-screens.md) — the component tree, and what a client does with a
  name it does not know

### Services (1)

- [x] [shashki-server](services/shashki-server.md) — one process that owns everything about a ride,
  and the list of what it deliberately does not do
