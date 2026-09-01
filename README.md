# shashki

A reference ride-hailing service — rider client, driver client, dispatch server — built to show the
kotlin.website stack working together on a domain anybody already knows how to judge: a car was
requested, a driver was assigned, the trip finished, the card was charged once.

It is not a demo of one library. The point is the seams. An order is a saga that survives the process
dying halfway; the events it emits leave the same transaction as the state change; some screens are
drawn natively and some are sent by the server; the design is accepted by a screenshot suite that
fails when the layout moves.

**Status: the seams first.** The order saga runs against a real Postgres and survives the process
dying at any phase; the offer is a suspended saga with a deadline; the map is drawn from the city's
own tiles onto a Compose canvas, and that image is a golden. The clients themselves are not built
yet. What holds it together is the architecture research — what was verified against the stack's own
artefacts and against upstream metadata, the decisions taken, and the risks with the machinery that
mitigates each — and a backlog ordered by it.

- [docs/](docs/) — the documentation tree, starting with
  [the architecture research](docs/research/research-architecture.md)
- [backlog.md](backlog.md) — what happens next, and why in that order

## Dependencies

Every dependency is a release or a CI-numbered publish; nothing is a `-SNAPSHOT` and nothing is a
dynamic version, on the plugin classpath either. Measured rather than intended: a clean checkout of
`HEAD` builds green on an empty Gradle cache, and `./gradlew :server:dependencies` and
`buildEnvironment` are re-read rather than the version table in the research remembered.

Three portfolio libraries are still ahead of this repository and are pre-release today — bochka,
katcher and smtpkn. Each is added by a backlog item that carries the pinning as its own acceptance
criterion, so the guarantee arrives with the dependency instead of being claimed for it in advance.

Two things pinning does not buy, named because they are easy to assume away: `ru.workinprogress` and
`io.github.youndie` artefacts live on one self-hosted repository, and a coordinate on its
`/snapshots` line could in principle be republished with different bytes. See the research's §1
amendment.

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
