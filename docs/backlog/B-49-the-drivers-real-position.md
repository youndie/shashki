---
id: B-49
title: "The driver's real position, from the browser, or the reason it stays configured"
status: question
priority: P2
size: S
stage: stage-5-the-rest-of-the-kit
blocked_by: [B-52]
---

# B-49 — The driver's real position, from the browser, or the reason it stays configured

B-29 stated it rather than stubbed it: the driver bundle sends its *configured* point, and movement
is `DriverSimulator`'s. The browser's Geolocation API needs a permission prompt and a device that is
going somewhere, and a fabricated drift would be the client inventing data the server indexes as
fact. All of that is still true, and it leaves the one real driver in any demo parked.

- **The question is whether a phone in a car is a demo this project gives.** If somebody opens the
  driver bundle on a phone and drives, the position socket should carry where they are — that is
  the product. If the demo is two browser tabs on a laptop, the configured point *is* the honest
  position and a permission prompt is noise.
- **If built: `navigator.geolocation.watchPosition` behind the same port the configured point uses**,
  so the socket does not know which it is fed, and a denied permission falls back to the configured
  point *and says so on the shift screen* — "position: configured" beside the count of reports, so a
  parked driver is a fact and not a bug.
- The rejected alternative is a fake walk along a route to make the demo look alive. B-29 already
  refused it and the reason has not changed.
- Deliberately **not** covered: background location, which a browser does not give a page anyway.

- AC (if built): on a device that grants permission, the shift screen's report count rises with
  positions the browser produced, and the rider's trip screen shows the car move.
- AC (either way): the shift screen names the source of the position it is sending.
- Anchors: `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/`,
  `docs/screens/screen-driver-shift.md`
