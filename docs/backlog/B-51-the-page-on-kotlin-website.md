---
id: B-51
title: "The page on kotlin.website, beside mani: what shashki shows, in the stack's own words"
status: open
priority: P2
size: S
stage: stage-4-elsewhere
---

# B-51 — The page on kotlin.website, beside mani: what shashki shows, in the stack's own words

Every library on kotlin.website has a page, and mani — the budget planner that "exists to show the
whole stack working at once" — has one too. shashki is the larger version of that claim, and there
is no page. The material is not the problem: forty-one items each carry a *What it turned out to be*,
and the research's §5 says what v1 shows and what it does not. The item is choosing which of it a
stranger needs.

- **The page is about the seams, not the screens.** The site's other pages lead with the thing that
  would not have been found without building it — the 46 MiB identity provider, the broker with no
  coordinator. Here that is the list this backlog kept producing: three mechanisms built at both
  ends and joined at neither (kompot, settlement, sign-in), the map that had to be drawn because
  nothing published draws one in Wasm, and the saga killed at every phase boundary. The screenshots
  are the goldens, which is itself a sentence for the page.
- **One number per claim, and its methodology in the repository**, as every other page on the site
  does it: 2.57 ms a route, 8 = 8 = 8 across two collectors, 1.5–3.7 s to healthy, 0.19 % on one
  character. A page that says "fast" beside a stack whose other pages say how fast would be the odd
  one out.
- **And the honest boundary.** §5's "what v1 does not show" goes on the page in the same voice as
  kvadrant's "what it is not" and smtpkn's platform claim. A reference that hides its edge is a demo.
- The rejected alternative is a blog post that narrates the two days. The write-ups already exist
  per item; the page is the map of them.
- Deliberately **not** covered: a hosted demo URL. That is B-35's image on somebody's cluster, and
  whether it runs publicly is a decision about a cluster.

- AC: `kotlin.website/shashki` exists, links the repository, and every number on it is traceable to
  an item or a research section by name.
- AC: the stack table on the page lists what is used, what is a host, and what was dropped — with
  D12 as the example of the third — so the page and the research's version table say the same thing.
- Anchors: `docs/research/research-architecture.md`, `docs/backlog/`
