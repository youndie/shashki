---
id: B-40
title: "The layers below the research, which the rule about main has been holding empty"
status: done
priority: P1
size: L
stage: stage-3-surface
---

# B-40 — The layers below the research, which the rule about main has been holding empty

`docs/` has a README, a research document, a backlog and templates. It has no `features/`, no
`screens/`, no `api/` and no `services/` — the four layers its own coverage map is built to check,
with the templates for all four sitting in `docs/templates/` unused. Thirty-four items are closed.

- **The rule that kept them empty was right and has stopped being right.** "`main` describes what
  exists; a document about something unbuilt documents intent as fact" is why nothing was written on
  day one, and it is why the tree is trustworthy today. The condition it was waiting for has been
  met several times over: the screens exist and are photographed, the endpoints exist and are
  tested, the service exists and has a saga in it. What the rule now protects is the absence itself.
- **The cost is already visible in this repository.** The auth tier of every route is a comment in a
  routing file, because `docs/api/endpoint-*.md` — where the `server-feature-impl` skill says the
  tier is a mandatory column — does not exist. The wait on a class tile, the two meanings of
  "cancelled", the promo screen's ownership: each is argued once, inside whichever backlog item
  happened to touch it, and a reader who wants "what does this product do" has a 1 649-line research
  document and no other door.
- **The research is the wrong document for it and always was.** It records *why the shape is this
  shape*, dated and cited, with wrong ideas kept beside their corrections. A feature document says
  what the system does today. Folding the second into the first is how a research document becomes
  a manual nobody trusts, because a reader cannot tell which sentences are history.
- The rejected alternative is generating them from the code. The coverage map's own script says why
  in its header: the machine owns membership and counters, the author owns meaning — a generated
  feature document has the titles and none of the reasons.
- Deliberately **not** covered: a document per screen for screens that are only components. The
  kit's `ClassTile` and `OfferCard` are documented by their goldens.

- AC: `features/`, `screens/`, `api/` and `services/` exist and hold documents for what is built —
  the order saga and its compensations, the offer cascade, the two client shells and their screens,
  every route this server answers, and the service itself.
- AC: every endpoint document carries its **auth tier**, and the tiers match the code — checked
  against `Application.kt` and the routing files rather than against memory. Two of them are
  currently "public, temporarily, and chosen", and that sentence belongs in a table somebody can
  read.
- AC: `make check` covers all four layers and the coverage map is no longer empty for any of them;
  `make report` shows BDD coverage above zero.
- AC: no document repeats the research. Where a reason exists, the document links to the section
  rather than restating it — a second copy of an argument is an argument that will drift.
- Anchors: `docs/README.md`, `docs/templates/feature.md`, `docs/templates/endpoint.md`,
  `docs/templates/screen.md`, `docs/templates/service.md`

## What it turned out to be

**Seventeen documents, twenty-seven scenarios, and one defect the writing found.**

The tree now has all five layers: one service, seven features, five screens and four endpoint
documents, grouped by feature rather than by path so a route's auth tier is read beside the reason for
it. Every scenario carries a link to a test, and `bdd_report.py --repos ..` confirms all twenty-seven
name something that exists — two of them had to be rewritten to name a fixture rather than a `.png`,
because "the golden is the automation" is true and unverifiable as written.

**The auth-tier column is what earned the exercise.** Its first draft had `GET /api/rides/{id}` and
`GET /api/rides/{id}/driver` as public, which is what a reader would guess. They are not: all four
rider routes are inside `riderRoutes()`, so all four are behind the token. Checking that against
`RideRouting.kt` rather than against memory found the document wrong — and then found something worse.

**`:rider` never signs in.** `SignInAttempt` is built, tested against a live shildik and proven in a
browser; the application's HTTP client attaches no `Authorization` header and nothing calls it. So
with a provider configured the rider bundle gets 401 on every ride route, and the demo works only
because the stand leaves the provider off. That is the **third** mechanism in this repository built at
both ends and joined at neither, after kompot and the settlement's three pieces —
[B-41](B-41-the-rider-actually-signs-in.md), filed at P0 because it is the one gap that makes a
documented guarantee a fiction.

**What the documents deliberately do not do is explain.** The research says *why the shape is this
shape*, dated, with wrong ideas kept beside their corrections; these say what the system does today
and link to the section rather than restating it. A second copy of an argument is an argument that
will drift, and this tree already has one document worth trusting.

**Where the quirks came from.** Every "Quirks" section here is a sentence that already existed as a
comment beside the code that surprised somebody — the blank registration, the two meanings of
cancelled, the offer with no address, the count of positions sent. Moving them into a document a
reader can find is most of what the layer is for; leaving them only in KDoc means they are found by
whoever is already in the file.

**Not covered, and stated:** there is no document per component. `ClassTile` and `OfferCard` are
documented by their goldens, and a screen document that listed a component's states would be a second
copy of a picture.
