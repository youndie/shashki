---
id: B-63
title: "The product has no driver record, so a rider is asked to rate an e-mail address"
status: open
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
