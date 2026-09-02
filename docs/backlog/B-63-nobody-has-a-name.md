---
id: B-63
title: "The product has no driver record, so a rider is asked to rate an e-mail address"
status: done
priority: P2
size: M
stage: stage-6-what-running-it-said
---

# B-63 — The product has no driver record, so a rider is asked to rate an e-mail address

R8 on the stand asks `how was rider@example.com?`. The kit asks `how was ivan?` over a card with a
photo, a car, a plate and an average. This product has no driver record at all: the class and the
rating on a position frame are self-reported, the earnings screen's day rolls at UTC because there is
nowhere to keep a timezone, and the assigned-ride card has a registration slot that is deliberately
blank. Four documents already name the same absence in their own words.

- **One absence, named in four places, is an item rather than four footnotes.** A driver row with a
  display name, a car and a plate turns the identifier into a person on R6, R8 and D-anything, and
  takes the class and the rating off the wire — where a client currently asserts both about itself.
- **It is also the security half of [B-52](B-52-driver-routes-behind-the-token.md)'s remainder.** A
  driver who tells the server their own class can pick which offers they are eligible for; a record
  the server reads instead ends that without a single new check.
- The rejected alternative is a display name in the token. It moves the problem to the provider and
  still leaves the car, the plate and the rating nowhere.
- Deliberately **not** covered: photographs. The kit has a photo slot and this product has an object
  store now ([B-47](B-47-driver-onboarding-and-the-object-store.md)), which makes it possible and not
  yet necessary.

- AC: a ride's driver reaches the rider's screens as a name, a car and a plate, and R8 asks about a
  person.
- AC: the class and the rating on a `DriverReport` are ignored in favour of the record, and the
  documents that call them self-reported say so no longer.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/Driver.kt`,
  `docs/api/endpoint-driver.md`, `docs/screens/screen-rider-finished.md`

## What it turned out to be

**One table, and four documents stopped saying the same thing in four different ways.** `drivers`
carries a name, a car, a plate and a class; `RideView.driver` carries the first three to the rider's
screens with the recorded average beside them, and R8 asks about *Ivan Sokolov* rather than about an
e-mail address the stand happens to sign both roles in as.

**The class moved off the wire, which is the security half of it.** A driver telling the server
which class they drive is a driver choosing which offers they are eligible for. The frame's
`rideClass` is not read at all now — `TheDriversOwnClassTest` sends `BUSINESS` from a driver the
record calls `ECONOMY` and asserts which list they land in, and it fails against the old code. The
rating had already moved in B-44, which is why only half of this was left.

**No registration, so the rows are seeded and the migration says so.** Inventing a record on first
sight would be the server making up somebody's car. The rule that comes with it is deliberate: a
driver the server has never heard of is **not indexed**, and the log names them — a driver who cannot
go online is a visible failure, where a driver silently promoted into a class nobody gave them is not.

**Five test suites had to say their drivers exist**, which is the rule working rather than a nuisance:
a fixture that put a car on the map without a record was a fixture asserting a driver could choose
their own class. `PostgresHarness.driver(...)` is one line each. The exception is
`SimulatorFollowsRoadsTest`, which has no database and is about geometry — its module binds a
`DriverRepository` that answers for everybody, which is what `fun interface` is for.

**What is still missing and now says so in one place**: the timezone. D6's day rolls at UTC because
the record has no zone in it, and that is a column and an item rather than a paragraph in four
documents.
