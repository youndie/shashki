---
id: B-41
title: "The rider application actually signs in, and puts the token on its requests"
status: open
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
