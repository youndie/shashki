---
id: B-52
title: "The driver's four routes stop being public: the hole the endpoint table names"
status: open
priority: P0
size: M
stage: stage-5-the-rest-of-the-kit
---

# B-52 — The driver's four routes stop being public: the hole the endpoint table names

`docs/api/endpoint-driver.md` lists `WS /api/driver/positions`, `GET /api/driver/offers/{driverId}`,
`POST /api/driver/offers/{rideId}/answer` and `POST /api/driver/rides/{rideId}/advance` as
**"public, temporarily, and the hole is named"**. Naming it was B-40's honesty; this is the item that
closes it. Today any client that knows a `driverId` can report a position for that driver, read the
offer waiting for them, accept it, and drive somebody else's trip to `COMPLETED` — which captures the
rider's hold. The rider side was closed by [B-41](B-41-the-rider-actually-signs-in.md); the driver
side has the same `Session` in the same `auth-client` and binds none of it to a request.

- **The driver's identity comes from the token, not from the path.** `{driverId}` in the offers route
  and the id inside a `DriverReport` frame both become the principal's subject; a route that takes an
  id the caller chose *and* a token is a route that has to compare them, and a route that has to
  compare them will one day not.
- **The socket is the awkward one and it is the important one.** A WebSocket upgrade carries no
  `Authorization` header from a browser. The choice — a short-lived ticket minted from the token and
  passed as a query parameter, or the token as the first frame — is this item's decision; the
  research gets the paragraph either way.
- The rejected alternative is a shared driver secret in `globalThis.SHASHKI`. It is one line, and it is
  the same secret for every driver, which is not authentication.
- Deliberately **not** covered: a driver *role* in shildik distinct from a rider's. One realm, one
  kind of user, and which bundle they opened is which role they are — until a rider opens the driver
  bundle and this item's successor exists.

- AC: every driver route returns 401 without a token, and `endpoint-driver.md`'s tier column reads
  "driver token" with no "temporarily" left in the file.
- AC: `DriverSimulator` signs in the same way a driver's application does — it is a client of the
  same API, and a simulator with a back door is the back door.
- AC: a position frame for a driver other than the token's subject is dropped and counted, not
  indexed; `MatchingTest` plants its known driver through a token rather than through an id.
- AC: the research records how the socket carries the token, and why the other way was worse.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/dispatch/DriverPositionRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/RideRouting.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/`,
  `docs/api/endpoint-driver.md`
