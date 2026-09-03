---
id: B-88
title: "Twelve guards need a stand, the stand runs for hours, and nothing ever pointed one at the other"
status: done
priority: P1
size: M
stage: stage-6-what-running-it-said
---

# B-88 — Twelve guards need a stand, the stand runs for hours, and nothing ever pointed one at the other

Counted while re-verifying closed items against a running stand (2026-09-04). `./gradlew check`
reports **468 tests, 12 skipped, 0 failed** — and the twelve are not a tail of odds and ends. They
are the seams:

| skipped guard | the claim nobody was checking |
|---|---|
| `SignInAgainstShildikTest` ×2 | [B-26](B-26-sign-in-end-to-end.md) — an attempt this code builds is one a real provider completes |
| `ProtectedRidesTest` | B-26 — and the token it produces is one this server accepts |
| `SignInJoinsUpTest` | [B-41](B-41-the-rider-actually-signs-in.md) — the application joins both halves |
| `RideEventsOverBooblikTest` | [B-38](B-38-ride-events-on-booblik.md) — a ride's events cross the broker and come back as history |
| `DocumentsAgainstBochkaTest` | [B-47](B-47-driver-onboarding-and-the-object-store.md) — a licence is refused to anybody without a token |
| `ReceiptOverSmtpTest` ×2 | [B-14](B-14-receipt-over-smtpkn-jvm.md) — the receipt goes out over a verified TLS handshake, and a CA that signed nothing is refused |
| `TilesOverHttpTest` ×2 | [B-07](B-07-serve-pmtiles-from-bochka.md) — the archive is read over ranges and the tiles decode |
| `CityGraphMeasurement` | [B-23](B-23-routes-and-eta-on-embedded-graphhopper.md) — a route across the city under 50 ms |
| `KatcherIngestTest` | [B-10](B-10-crash-reports-from-the-browser.md) — a crash reaches a running katcher |

**The gate is right and the absence is not.** Each is `assumeTrue`-gated on a service being named,
because CI has none and a suite that fails for want of a container is a suite people learn to skip.
But the services do exist: `docker/compose.yaml` stands up shildik, bochka, booblik and the rest, and
on the build box they run for hours at a time. Nothing pointed the guards at them. So every one of
these claims rested on a run somebody did by hand, once, months ago — and this repository already
knows what that is worth, because it is the same shape as the finding in
[B-87](B-87-the-recorded-reproduction-leaves-its-own-control-skipped.md) one layer down.

- AC: one command runs every guard the stand can satisfy, against it.
- AC: that command asserts on the **reports**, not the exit code — `assumeTrue` skips are green, so a
  run that satisfied nothing must not look like a run that proved everything.
- AC: the guards the stand cannot satisfy are named as uncovered rather than passed over.
- Anchors: `scripts/stand-tests.sh`, `map/city_tiles.sh`

## What it turned out to be

**`scripts/stand-tests.sh`, and it found two things on its first two runs.**

The script exports the addresses `docker/compose.yaml` publishes, starts a Mailpit with two
certificates for the length of the run (the second is the control's — B-87), runs each module's test
task as its own invocation, and then reads the JUnit XML: it fails unless every guard produced a
report *written by this run* and skipped nothing. katcher is not in the stand, so its guard is
reported as uncovered by name.

**Its first version lied, in the way this repository keeps finding.** Written as one Gradle command
listing five tasks, `--tests` filters bound to the wrong tasks, `:server:test` never executed, its
report from four minutes earlier stayed on disk, and the summary read that as this run's result —
nine guards reported skipped when the tasks holding them had not run at all. The timestamps are what
said so. The script now records when it started and ignores any report older than that, and says
plainly when an older one is on disk.

**Two real defects, found by running what had not been run:**

- **The graph the map pipeline builds collides with the one the server looks for.** `map/city_tiles.sh`
  wrote its measurement graph to `$OUT/graph-cache`, and `RoutingConfig` resolves the server's graph
  directory as exactly that sibling of the extract. The two are imported by different tools with
  different profiles, so anything pointed at the extract afterwards dies with
  `Profiles do not match: car|-1705186244 vs car|26199302` — the landmine [B-35](B-35-the-server-as-an-image.md)
  recorded for images, sitting on disk instead. `CityGraphMeasurement` had already sidestepped it
  with a private suffix, which is the tell nobody read. The step now writes `graph-cache-import`.
- **[B-89](B-89-the-forgery-that-forged-nothing-one-run-in-four.md)**: `ProtectedRidesTest`'s forged
  signature is unchanged one run in four, and the test then reports the server as having accepted a
  broken one.

With both fixed, the stand's guards run and pass — eleven of twelve, with katcher named as the one
this stand cannot answer for.

**What this does not become is a CI job.** The services are the point of the gate: CI has no shildik
and buying one would trade a skip nobody sees for a queue everybody waits on. What was missing was
never the automation, it was the *one line* that a person with a stand up could run — and the count
that tells them whether it actually checked anything.
