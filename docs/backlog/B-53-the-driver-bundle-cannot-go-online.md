---
id: B-53
title: "The driver bundle sends an id the token contradicts, so every position frame is dropped"
status: open
priority: P0
size: S
stage: stage-6-what-running-it-said
---

# B-53 — The driver bundle sends an id the token contradicts, so every position frame is dropped

Against a server with a provider configured — which is the stand as `docker/compose.yaml` sets it up
— the driver bundle cannot go online at all. It puts `driverId` in every `DriverReport`, taken from
`globalThis.SHASHKI.driverId` and falling back to `"driver-1"` when `SHASHKI_DRIVER_ID` is unset
(`driver/src/wasmJsMain/.../Main.kt`). The socket's identity is the token's subject, and
[B-52](B-52-driver-routes-behind-the-token.md) drops a frame that claims anybody else rather than
relabelling it. Measured on the running stand: one line in the server log per frame —
`WARN shashki.positions - a socket reported a position for a driver it is not signed in as` — the
driver never enters the geo-index, `pickupEtaSeconds` is `null` for every class, and an order finds
no candidate and cancels.

- **The identity is the token's, and the client should stop claiming one of its own.** B-52 settled
  that for every HTTP route: the subject replaces the claimed value and there is no branch in which
  they can disagree. The socket compares instead of replacing for a good reason — relabelling would
  file somebody else's car under this driver — but a client that simply does not claim an id has
  nothing to misfile. The fix is on the client: when there is a token, the driver id **is** its
  subject, and `SHASHKI_DRIVER_ID` goes back to being what it was meant to be, the demo's answer for
  a server with no provider.
- The alternative — setting `SHASHKI_DRIVER_ID` in the compose file to the seeded user's e-mail — is
  worse: it makes the stand work and leaves the product broken for anybody who signs in as somebody
  else, which is every real deployment.
- The other alternative, making `DriverReport.driverId` nullable on the wire, is a protocol change
  for a value the server already ignores on every other route. Worth considering *after* the client
  stops guessing, not instead of it.
- Deliberately **not** covered: the offers poll and the earnings/documents routes take the id in the
  path and are unaffected, because the server replaces it there.

- AC: signed in on the stand, pressing *go online* puts the driver in the index — a quote for the
  same pickup answers with an ETA rather than `null`, and an order reaches that driver as an offer.
- AC: with no provider configured, the configured id still works — the demo path is not broken by the
  fix.
- AC: a test drives the socket with a token whose subject differs from `SHASHKI_DRIVER_ID` and
  asserts the driver reaches the index. The control is the same test before the fix.
- Anchors: `driver/src/wasmJsMain/kotlin/io/github/youndie/shashki/driver/Main.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/shift/domain/GoOnlineUseCase.kt`,
  `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/`
