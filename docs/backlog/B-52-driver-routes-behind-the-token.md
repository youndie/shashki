---
id: B-52
title: "The driver's four routes stop being public: the hole the endpoint table names"
status: done
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

## What it turned out to be

**The decision the item left open had an answer with evidence, and the evidence was a visibility
modifier.** A browser cannot put a header on a WebSocket, so the upgrade needs something else. The
two candidates were the token as the first frame and a short-lived ticket in the query — and the
first one requires the server to verify a raw JWT itself, because shildik's `TokenVerifier` is
`internal`. Taking it would mean shashki implements "is this signature ours" a second time, which is
the one thing its own service document forbids. So: `POST /api/driver/ticket` behind the ordinary
`authenticate` block, thirty seconds, single use, no claims. A value in a query string reaches access
logs and browser history — the reason a token must never go there, and the reason something worth
almost nothing may.

**The identity replaces rather than compares, except in the one place where replacing is worse.**
`driverIdentity` takes the subject and ignores the path segment and the body field, so there is no
branch in which the two can disagree; the fields survive only because a server with no provider is a
running configuration and there they are the only source. The socket is the exception the item's own
third criterion asks for: a frame for another driver is dropped and *counted*, because relabelling it
would file somebody else's car under the connected driver, and a count is what makes a client whose
id and token disagree visible instead of merely absent.

**Two things came out of the drawer while this was open.** `Session`, `TokenExchange` and
`HttpTokenExchange` lived in `:rider`, so the driver could not have signed in the same way even if it
had wanted to — they are in `:auth-client` now, which is where the item assumed they already were.
And the driver bundle is served under `/driver` while its routes say `/`, so it had been pushing the
*rider's* address into the bar all along: a refresh landed on the wrong application, and a redirect
URI computed from `origin() + "/callback"` would have been the rider's too. `AddressBar.under(prefix)`
is that fix, with its own test.

**Verified on the stand as well as in tests.** With `SHASHKI_OIDC_ISSUER` set, all four driver routes
answer 401 without a token — offers `401`, answer `401`, advance `401`, ticket `401` — while
`/api/quotes` stays `200`, because a price is a fact about the city. A socket opened with no ticket
still completes its upgrade (there is no status to send once a handshake is accepted) and is closed
before a frame is read: the quote right after it says `pickupEtaSeconds: null`, so nobody reached the
index.

- AC 1: done, and `endpoint-driver.md`'s tier column reads "driver token" with no "temporarily" left
  in the file.
- AC 2: `SimulatorConfig` grew `token` and `ticket` lambdas and every request it makes goes through
  them — a simulator with a back door is the back door.
- AC 3: `a position frame for another driver is dropped and counted`, and `MatchingTest` plants its
  known driver through a ticket the server minted for that subject rather than through an id it
  chose.
- AC 4: [research §1.6c5](../research/research-architecture.md), with the comparison and the reason
  the other way was worse.

**What is deliberately still open**: the class and the rating on a position frame are self-reported,
because there is no driver record to read them from; and a driver *role* distinct from a rider's is
the item's own exclusion — one realm, one kind of user, and which bundle somebody opened is which
role they are.
