---
id: B-49
title: "The driver's real position, from the browser, or the reason it stays configured"
status: done
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

## What it turned out to be

**Built, and the answer to the question it was filed as is "both".** A phone in a car is a demo this
project gives — `navigator.geolocation.watchPosition` behind `PositionFixes`, so the socket cannot
tell which it is being fed — and a laptop with two tabs is still the honest configured point, now
labelled on the screen instead of being a thing you had to know.

**The cadence is the bundle's and not the device's, which the item did not anticipate.**
`watchPosition` fires when a phone decides it has something to say: several times a second in a
moving car, never in a parked one. The server's index wants neither. So the fixes go into a cell that
keeps the newest and the four-second ticker reads it — a driver standing still still reports, and a
driver on a motorway does not flood the socket. The test asserts exactly that: four fixes between two
ticks produce one report, carrying the last of them.

**The fallback is silence, not an error.** A denied permission, a desktop window, and a page served
over plain HTTP to anything but `localhost` — where a browser withholds the API rather than prompt —
are one case here, and it is the case that must not take a shift offline: a driver is entitled to
withhold a permission and still be on the map. What the screen owes them is the truth about what it
is sending, which is the line `42 positions sent · position: configured`, photographed in both
themes, and `position: device` photographed beside it because that state exists only on a phone and
nobody would otherwise see it.

**The AC's device half is not verified here and this says so.** Granting a real permission needs a
real device with a real person on it, which this session does not have. What *is* verified is
everything either side of it: the port's takeover and its fallback under a test clock, and — the part
that only a browser can answer — the interop itself, in the headless Chrome `check` already uses. A
`js("…")` block compiles whatever it contains: "it builds" says nothing about whether `watchPosition`
is reachable, whether a Kotlin lambda survives being handed over as a browser callback, or whether
cancelling calls `clearWatch` on something that exists. `DeviceLocationTest` runs it and asserts the
two things true of a browser that granted nothing — nothing throws, and no position appears.

**A defect found on the way, and deliberately not fixed.** The light golden of this screen has the
word `waiting` in the accent on white at **2.11:1** — the same number, the same amber, the same
white as B-48's offer-card fare, on the largest word on a driver's screen. B-48's own light goldens
already contained it; nobody had read that picture. It is left alone for B-48's own reason and by its
own precedent: "amber means online" is the kit's design, and this product changing the hue would be
this product editing the thing it exists to demonstrate. It is recorded in the research beside the
rule. The lesson is the one worth keeping: **a golden is a photograph, not an assertion — it reports
only what somebody looks at.**
