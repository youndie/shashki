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

**Only `research/` exists today, and that is not an omission.** There is no shashki source tree yet.
A feature, screen, endpoint or service document written now would describe intent, and the one rule
this tree is built on is that `main` describes what exists. The four lower layers arrive with the
code they describe, one feature at a time, starting with the order saga.

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
  in §1.8 rather than imagined), the nine decisions taken, and six risks with the machinery that
  mitigates each
