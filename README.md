# shashki

A reference ride-hailing service — rider client, driver client, dispatch server — built to show the
kotlin.website stack working together on a domain anybody already knows how to judge: a car was
requested, a driver was assigned, the trip finished, the card was charged once.

It is not a demo of one library. The point is the seams. An order is a saga that survives the process
dying halfway; the events it emits leave the same transaction as the state change; some screens are
drawn natively and some are sent by the server; the design is accepted by a screenshot suite that
fails when the layout moves.

**Status: research.** There is no source tree yet. What exists is the architecture research — what
was verified against the stack's own artefacts and against upstream metadata, the decisions taken,
and the risks with the machinery that mitigates each — and a backlog ordered by it.

- [docs/](docs/) — the documentation tree, starting with
  [the architecture research](docs/research/research-architecture.md)
- [backlog.md](backlog.md) — what happens next, and why in that order

## Checks

```bash
pip install pyyaml
make check
```

`make report` runs the two non-blocking reports. Anchors resolve against the sibling repositories of
this stack, so `make report` is only meaningful where those are checked out beside this one.

## Language

Code, comments, test names, exception messages and commit subjects are in English, and so is this
documentation tree. The design kit and the product brief it was derived from are in Russian and stay
that way: they are evidence, and evidence is not translated.
