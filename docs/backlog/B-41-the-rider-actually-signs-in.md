---
id: B-41
title: "The rider application actually signs in, and puts the token on its requests"
status: done
priority: P0
size: M
stage: stage-3-surface
---

# B-41 — The rider application actually signs in, and puts the token on its requests

`SignInAttempt` is built, unit-tested against RFC 7636's own vector, proven against a running shildik
and proven again in a real browser. The server verifies tokens with shildik's own validator and
refuses a forged signature. **Nothing joins them.** `:rider` never calls `SignInAttempt`, its HTTP
client attaches no `Authorization` header, and `RiderRoute.SignIn` renders the class picker.

So with `SHASHKI_OIDC_ISSUER` set, the rider bundle gets **401 on every ride route** — `POST
/api/rides`, `GET /api/rides/{id}`, the cancel and the driver's position are all inside
`riderRoutes()`. The demo works today only because the stand leaves the provider unconfigured, and
`docker/compose.yaml` has that line commented out for exactly this reason.

- **This is the third mechanism in this repository built at both ends and joined at neither**, after
  kompot ([B-32](B-32-which-screens-the-server-sends.md)) and the settlement's three pieces
  ([B-37](B-37-the-settlement-saga.md)). It was found by writing
  [feature-sign-in](../features/feature-sign-in.md) down — the layers doing what
  [B-40](B-40-the-documentation-layers.md) said they would.
- **P0, because it is the one gap that makes a documented tier a fiction.** Every other absence in
  this product is stated and costs a feature; this one costs the *guarantee*: the auth tier of five
  routes is written in three places and no client has ever satisfied it.
- **The token has to survive a redirect**, which is what makes this more than three lines: the
  authorization flow leaves the page and comes back, so the verifier and the state have to outlive
  the navigation. `sessionStorage` is the obvious place and its cost — a token readable by any script
  on the origin — is the decision this item takes.
- The rejected alternative is putting a `riderId` in the body for ever and calling the routes public.
  It is what the code does today, and it is why B-09's remainder keeps growing.

- AC: opening the rider bundle against a server with a provider configured leads to a sign-in and back,
  and the class picker prices a journey with a token on the request.
- AC: the token is attached by the HTTP client rather than by each repository — one place, so a route
  added tomorrow is authenticated tomorrow.
- AC: a 401 from the server is a sign-in, not an error banner: an expired token is the ordinary case.
- AC: `docker/compose.yaml` sets `SHASHKI_OIDC_ISSUER` and the stand still reaches a priced class
  picker — the comment that turns it off is deleted rather than left as a warning.
- AC: the rider's email reaches the order saga from the principal, so the settlement has an address
  and `feature-settlement`'s receipt scenario stops depending on a payload built by a test.
- Anchors: `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/RiderModule.kt`,
  `auth-client/src/commonMain/kotlin/io/github/youndie/shashki/auth/SignIn.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/ride/RideRouting.kt`

## What it turned out to be

**The join itself was small. Three things around it were not, and none of them was about PKCE.**

`Session` is sixty lines: park an attempt, hand the tab over, exchange the code when it comes back,
forget the token when the server refuses it. The token is attached in `defaultRequest` — one place,
so a route added tomorrow is authenticated tomorrow — and a 401 calls `renew()`, because an expired
token is the ordinary case and a banner telling somebody to try again is advice they cannot act on.

**A provider must have one address, and "each side uses what it can reach" is a wrong model.** The
first attempt gave the server `http://shildik:8080` and the browser `http://127.0.0.1:18081`, which
is exactly what a container network suggests. It does not work: the validator reads `jwks_uri` out of
the **discovery document**, which carries the issuer's own address — so the container fetched
discovery from inside the network and was then sent to an address only a browser can reach. Every
token came back 401, which is indistinguishable from a token that is wrong. The stand now uses the
machine's own address for both, and `SHASHKI_HOST` is why the compose file asks for one.

**A relative script URL breaks every deep link, and this found it.** `index.html` asked for
`rider.js`; at `/trip/ride-1` that resolves to `/trip/rider.js`, which the static route answered with
the page itself — an HTML file served as JavaScript, a blank window, and B-28's promise that
`/trip/{id}` is a real address quietly broken since the image existed. `<base href>` is one line.

**A test that navigates cannot be a browser test.** `Session.begin` called `redirectTo` directly, so
running its tests in a real Chrome took karma's own page to shildik and failed the whole wasm suite.
The redirect is now a constructor parameter — which is better design for a reason that has nothing to
do with testability: what the browser was sent to is now something an assertion can look at, and the
test checks that the challenge is in the URL and **the verifier is not**.

**And the build caught a test that was not there.** The join test was written
`fun … = runBlocking { … }`, whose last expression returns a value: the method is non-void, JUnit does
not collect it, and it reported nothing while the build went green. sborka's own guard failed with
"declares 1 @Test and reported nothing at all".

**What is proven, and by what.** Six unit tests for the session either side of the navigation. One
gated test that drives the application's own `Session` and `HttpTokenExchange` through a live shildik
and then orders a ride on the live server with what it got — the same request without the token is
401, which is the control. What is **not** covered is the redirect itself: `redirectTo` and
`sessionStorage` are a browser, and the page's half was checked by hand against the stand.

**AC1 as written asked for a sign-in on open and it does not happen on open.** Prices are public — the
endpoint table says so and the tiers were written before this item — so the class picker loads
anonymously and the redirect happens on the first protected call. Signing somebody in to show them a
price would contradict the tier rather than satisfy it. The criterion is met in substance: a sign-in
happens, and the ride is ordered with a token.

**The email now reaches the saga from the principal**, checked in the stand's own database rather than
in a test's fixture: `payload->>'riderEmail'` is `rider@example.com` after a ride ordered through the
flow.
