# shashki — how to start a session

Read in this order. It is short because the whole point of the tree is that you do not have to read
all of it.

1. **[docs/research/research-architecture.md](docs/research/research-architecture.md)** — first,
   always. It is what separates "do the obvious thing" from "do the thing that works". Four of the
   brief's assumptions turned out to be wrong when checked, and the research is where each is
   recorded next to the artefact that disproved it. A task read without it will re-derive one of
   them.
2. **[backlog.md](backlog.md)** — the stages and the order. Items are one file each in
   [`docs/backlog/`](docs/backlog/) and are cited by id from everywhere.
3. **The layer document the task belongs to** — `docs/features/`, `docs/screens/`, `docs/api/`,
   `docs/services/`. None of these exist yet; see below.

## Which skill to work through

Feature work goes through a skill rather than through improvisation, and which one follows from
which half of the product the task is in.

| The task is | Use |
|---|---|
| a server feature or endpoint — domain and data layers, repository, use case, routing, the DI module | **`server-feature-impl`** |
| a client feature — repository / use case / view model, the Screen/Content split, wiring through DI | **`client-feature-impl`** |

Invoke the skill first and follow it; this file does not restate what it says. The division of labour
is the point — a skill carries *how* the layers are built and changes when that changes, while this
file says *which* one applies here and would go stale the moment it tried to summarise one.

A task that is neither — the build, the documentation tree, a spike answering a research question —
has no skill and is done directly against the backlog item.

## The rule the tree is built on

**`main` describes what exists. An open pull request describes what will be.** A feature that is
designed but not shipped is `status: draft` and lives in a branch. This is why there is no
`features/`, `screens/`, `api/` or `services/` directory today: writing one now would document
intent as fact, and a document that looks authoritative about something that does not exist is worse
than a missing one.

The second rule is the reason the research is readable at all: **what was verified is separated from
what was assumed, explicitly.** Every fact in §1 carries the artefact it was read out of. If you add
one, add its address. If you cannot verify something, say the document does not cover it — an
admitted gap costs a reader nothing, an invented detail costs them the whole file.

## When research and reality disagree

Amend **the research**, at the point of divergence: "this used to say take X — you cannot, because Y;
the working replacement is Z". Do not delete the wrong idea. It is the part that comes back.

## Where things are built

Builds and tests run on the Linux build box rather than on the editing machine, through the wrapper
the global agent instructions name — it finds the sync session for the current directory itself.

What genuinely cannot be built there stays local: Apple targets, `xcodebuild`, the simulator, and
anything prefixed `LOCAL=1`. **Formatters run locally.**

**Goldens are recorded on this machine and verified everywhere.** `LOCAL=1 ./gradlew
:shared-ui:viddikRecord` writes them; `./gradlew check` compares them, and it does so identically on
the mac, on the Linux box and on CI — measured, not assumed:
[B-02](docs/backlog/B-02-measure-golden-host-independence.md). Look at a re-recorded PNG before
committing it; a screenshot of the wrong thing verifies just as well as one of the right thing.

Edits are made in the working copy on this machine. The directory on the Linux side is a replica:
work done there reaches neither git nor here.

## Commits

Conventional Commits, in English, always — regardless of the language of the conversation, of the
documentation, or of the previous commits. No tool signatures in the message. The same applies to
pull request titles and bodies and to branch names.

## Documentation checks

```bash
pip install pyyaml
make check     # the gate: index, connectivity, coverage map
make report    # non-blocking: BDD coverage, code anchors
```

After editing a backlog item run `python3 scripts/backlog_index.py` and commit both files. The tables
between the `BEGIN INDEX` / `END INDEX` markers in `backlog.md` are generated; everything else in
that file is written by hand and is never touched.

The format itself is [docs/templates/](docs/templates/), copied in so it travels with the repository.
